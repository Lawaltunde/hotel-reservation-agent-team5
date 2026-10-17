package com.reservationagent.hotelreservationagent.application.service.notification;

import com.reservationagent.hotelreservationagent.application.port.out.AiUsageRepository;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Email;
import com.reservationagent.hotelreservationagent.domain.call.CallOutcome;
import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.CallRecording;
import com.reservationagent.hotelreservationagent.application.port.out.EmailProvider.Attachment;
import com.reservationagent.hotelreservationagent.application.service.recording.CallRecordingService.IssuedLink;
import com.reservationagent.hotelreservationagent.domain.call.PhoneCall;
import com.reservationagent.hotelreservationagent.domain.hotel.Hotel;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.OfferPrice;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.domain.reservation.Reservation;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import com.reservationagent.hotelreservationagent.domain.task.TaskOutcome;
import com.reservationagent.hotelreservationagent.domain.transcript.Transcript;
import com.reservationagent.hotelreservationagent.domain.transcript.TranscriptTurn;
import com.reservationagent.hotelreservationagent.domain.task.TaskIntake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Builds the single report email in the organizer's format: the cheapest confirmed-available
 * option (hotel, address, number called, stay dates, total price, reference # if given), then the
 * evidence (tokens spent, recording and conversation links, transcripts inline). Every quote is
 * reported as a quote: nothing is booked, held or paid.
 */
@Component
public class ReportComposer {

    private static final String UNKNOWN = "Unknown";
    public static final String DEFAULT_SUBJECT = "Team # 5 - Hotel Reservation Confirmed!";
    public static final String DEFAULT_SUBJECT_UNCONFIRMED = "Team # 5 - Hotel enquiry: no confirmed availability";
    /** The body never repeats the subject: the body states plainly that nothing was booked. */
    static final String HEADING = "Team 5 - hotel enquiry result";
    static final String NO_RESERVATION = "None provided (no reservation made, as instructed)";
    public static final String CURRENCY = Money.CURRENCY;

    private final String subject;
    private final String subjectUnconfirmed;
    private final String reportRecipient;
    private final OfferEvaluator evaluator;
    private final String from;
    private final Clock clock;
    private final String signOff;

    public static final String DEFAULT_TEAM_NAME = "Team 5";
    public static final String DEFAULT_TEAM_MEMBERS = "Hammed Lawal <lawalolatunde14@gmail.com>, "
            + "Julia Bougaev <janojuly@gmail.com>, Aakash Uma Maheswaran <aakash.maheswaran@gmail.com>";

    /** With the default team sign-off. */
    public ReportComposer(String subject, String subjectUnconfirmed, String reportRecipient, String from,
                          OfferEvaluator evaluator, Clock clock) {
        this(subject, subjectUnconfirmed, reportRecipient, from, DEFAULT_TEAM_NAME, DEFAULT_TEAM_MEMBERS, evaluator, clock);
    }

    @Autowired
    public ReportComposer(@Value("${app.report.subject:" + DEFAULT_SUBJECT + "}") String subject,
                          @Value("${app.report.subject-unconfirmed:" + DEFAULT_SUBJECT_UNCONFIRMED + "}") String subjectUnconfirmed,
                          @Value("${app.report.recipient:}") String reportRecipient,
                          @Value("${hotel-agent.resend.from:}") String from,
                          @Value("${app.report.team-name:" + DEFAULT_TEAM_NAME + "}") String teamName,
                          @Value("${app.report.team-members:" + DEFAULT_TEAM_MEMBERS + "}") String teamMembers,
                          OfferEvaluator evaluator,
                          Clock clock) {
        // A blank env var (copied .env.example) means "use the defaults", like the other report settings.
        this.signOff = signOff(teamName, teamMembers == null || teamMembers.isBlank() ? DEFAULT_TEAM_MEMBERS : teamMembers);
        this.subject = subject;
        this.subjectUnconfirmed = subjectUnconfirmed;
        this.evaluator = evaluator;
        this.reportRecipient = reportRecipient == null ? "" : reportRecipient.strip();
        this.from = from == null || from.isBlank() ? null : from.strip();
        this.clock = clock;
    }

    public record ReportInput(BookingTask task, String city, TaskOutcome outcome, HotelOffer bestOffer, Hotel hotel,
                              Reservation reservation, List<Transcript> transcripts, int screened, int called) {
    }

    /**
     * Everything about the calls that the report shows as evidence.
     *
     * @param audio the recordings fetched in time, by call ID; a call missing here gets a link instead
     */
    public record ReportContext(List<CallEvidence> calls, AiUsageRepository.Totals usage,
                                Map<UUID, CallRecording> audio) {
        public ReportContext {
            audio = audio == null ? Map.of() : Map.copyOf(audio);
        }

        public ReportContext(List<CallEvidence> calls, AiUsageRepository.Totals usage) {
            this(calls, usage, Map.of());
        }
    }

    /** Attachments stay under this so the email fits Gmail's 25 MB limit after base64 encoding. */
    public static final long MAX_ATTACHMENT_BYTES = 18L * 1024 * 1024;

    /**
     * The calls whose audio and transcript are attached, chosen hotel first, then the other answered
     * calls in call order. Voicemail and calls without a transcript are skipped.
     */
    public static List<CallEvidence> attachableCalls(List<CallEvidence> calls, UUID chosenCallId) {
        List<CallEvidence> answered = calls.stream()
                .filter(c -> c.transcript() != null && !c.transcript().turns().isEmpty())
                .filter(c -> c.call().outcome() != CallOutcome.VOICEMAIL)
                .toList();
        List<CallEvidence> ordered = new ArrayList<>();
        answered.stream().filter(c -> c.call().id().equals(chosenCallId)).forEach(ordered::add);
        answered.stream().filter(c -> !c.call().id().equals(chosenCallId)).forEach(ordered::add);
        return ordered;
    }

    /** The files to attach and a note for each recording that could not be attached. */
    record AttachmentPlan(List<Attachment> files, List<String> notes) {
    }

    private static AttachmentPlan plan(ReportContext ctx, Optional<CallEvidence> chosen) {
        List<Attachment> files = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        long total = 0;
        int n = 0;
        for (CallEvidence c : attachableCalls(ctx.calls(), chosen.map(e -> e.call().id()).orElse(null))) {
            n++;
            String hotelName = c.hotel() == null ? "hotel" : c.hotel().name();
            String base = "call-" + n + "-" + slug(hotelName);
            byte[] transcript = timedTranscript(c.transcript()).getBytes(StandardCharsets.UTF_8);
            CallRecording audio = ctx.audio().get(c.call().id());
            String link = c.recording().url() != null ? c.recording().url() : c.recording().text();
            if (audio == null) {
                notes.add(hotelName + ": recording not yet available from ElevenLabs; link: " + link);
            } else if (total + audio.audio().length + transcript.length > MAX_ATTACHMENT_BYTES) {
                notes.add(hotelName + ": recording not attached (email size limit); link: " + link);
            } else {
                files.add(new Attachment(base + ".mp3", "audio/mpeg", audio.audio()));
                total += audio.audio().length;
            }
            if (total + transcript.length <= MAX_ATTACHMENT_BYTES) {
                files.add(new Attachment(base + "-transcript.txt", "text/plain", transcript));
                total += transcript.length;
            }
        }
        return new AttachmentPlan(files, notes);
    }

    /** "[mm:ss] Agent: ..." lines. */
    static String timedTranscript(Transcript transcript) {
        StringBuilder s = new StringBuilder();
        for (TranscriptTurn turn : transcript.turns()) {
            long secs = turn.offset() == null ? 0 : turn.offset().toSeconds();
            s.append(String.format("[%02d:%02d] ", secs / 60, secs % 60))
                    .append(turn.speaker() == TranscriptTurn.Speaker.AGENT ? "Agent: " : "Hotel: ")
                    .append(turn.text()).append('\n');
        }
        return s.toString();
    }

    static String slug(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("-$", "");
        return s.isEmpty() ? "hotel" : s;
    }

    /** One call: who was called, what they quoted (if anything), and links to the recording. */
    public record CallEvidence(PhoneCall call, Hotel hotel, HotelOffer offer, Transcript transcript, IssuedLink recording,
                               String conversationUrl) {
    }

    /** Who gets the report. */
    public record Recipients(String to, List<String> cc) {
    }

    public record Composed(Email email, SentReport report) {
    }

    public Composed compose(ReportInput in, ReportContext ctx) {
        BookingTask t = in.task();
        Optional<CallEvidence> chosen = chosen(in, ctx);
        HotelOffer offer = chosen.map(CallEvidence::offer).orElse(null);
        Hotel hotel = chosen.map(CallEvidence::hotel).orElse(null);
        OfferPrice price = offer == null ? null : evaluator.price(offer).orElse(null);
        String subject = subject(chosen.isPresent());
        StayDetails stay = t.stay();

        StringBuilder b = new StringBuilder();
        b.append(HEADING).append("\n\n");
        if (chosen.isPresent()) {
            b.append("Cheapest confirmed available option among the hotels called:\n\n");
            b.append("Hotel Name: ").append(hotel == null ? UNKNOWN : hotel.name()).append('\n');
            b.append("Hotel Address: ").append(hotel == null ? UNKNOWN : orUnknown(hotel.address())).append('\n');
            b.append("Hotel Phone (number called): ").append(chosen.get().call().dialledNumber().e164()).append('\n');
            b.append("Stay: ").append(stayText(stay)).append('\n');
            b.append("Price for the stay: ").append(priceText(price)).append('\n');
            b.append("Reservation #: ").append(reference(offer) == null ? NO_RESERVATION : reference(offer)).append('\n');
        } else {
            b.append("No confirmed available room was found among the ").append(ctx.calls().size())
                    .append(" hotel(s) called.\n");
            b.append("Stay asked for: ").append(stayText(stay)).append('\n');
        }
        b.append("\nThis is an enquiry result only. No booking, reservation, hold or payment was made; ")
                .append("the hotel was told we would confirm later.\n");

        if (t.intake().originalPrompt() != null) {
            b.append("\nRequest: \"").append(t.intake().originalPrompt().strip()).append("\"\n");
        }
        List<String> assumptions = assumptions(t, chosen);
        if (!assumptions.isEmpty()) {
            b.append("Assumptions: ").append(String.join("; ", assumptions)).append('\n');
        }

        b.append("\nHotels called (").append(ctx.calls().size()).append(", ").append(in.screened())
                .append(" screened):\n");
        for (CallEvidence c : ctx.calls()) {
            b.append(" - ").append(c.hotel() == null ? UNKNOWN : c.hotel().name()).append(": ").append(callSummary(c))
                    .append('\n');
        }

        AiUsageRepository.Totals usage = ctx.usage();
        b.append("\nEvidence\n");
        b.append("Tokens spent: ").append(usage.tokens()).append(" (OpenAI prompt ").append(usage.promptTokens())
                .append(" + completion ").append(usage.completionTokens()).append("); ElevenLabs call time: ")
                .append(usage.callSeconds()).append(" s\n");
        chosen.ifPresent(c -> {
            b.append("Call recording: ").append(c.recording().text()).append('\n');
            b.append("Conversation: ").append(c.conversationUrl() == null ? "Unavailable" : c.conversationUrl()).append('\n');
        });

        List<SentReport.TranscriptEntry> transcripts = ctx.calls().stream()
                .filter(c -> c.transcript() != null)
                .map(c -> new SentReport.TranscriptEntry(c.hotel() == null ? UNKNOWN : c.hotel().name(),
                        c.call().conversationId(), c.conversationUrl(), c.recording().url(), c.call().startedAt(),
                        c.transcript().asPlainText()))
                .toList();
        AttachmentPlan attachments = plan(ctx, chosen);
        if (!attachments.files().isEmpty() || !attachments.notes().isEmpty()) {
            b.append("\nAttachments: ").append(attachments.files().isEmpty() ? "none"
                    : attachments.files().stream().map(Attachment::filename).collect(Collectors.joining(", "))).append('\n');
            attachments.notes().forEach(note -> b.append(note).append('\n'));
        }

        b.append("\nCall transcripts\n");
        if (transcripts.isEmpty()) {
            b.append("No call transcripts.\n");
        }
        for (SentReport.TranscriptEntry e : transcripts) {
            b.append("\n=== ").append(e.hotelName()).append(" (conversation ").append(e.conversationId()).append(") ===\n");
            b.append("Recording: ").append(e.recordingUrl() == null ? "Unavailable" : e.recordingUrl()).append('\n');
            b.append("Conversation: ").append(e.conversationUrl() == null ? "Unavailable" : e.conversationUrl()).append('\n');
            b.append(e.text()).append('\n');
        }
        b.append("\nHotel data: Google Places, OpenStreetMap contributors.\n\n").append(signOff);

        Recipients recipients = recipients(t);
        String body = b.toString();
        Email email = new Email(recipients.to(), recipients.cc(), subject, body, attachments.files());
        SentReport.Fields fields = new SentReport.Fields(
                hotel == null ? null : hotel.name(),
                hotel == null ? null : hotel.address(),
                chosen.map(c -> c.call().dialledNumber().e164()).orElse(null),
                offer == null ? null : reference(offer),
                stay.checkIn(), stay.checkOut(),
                price == null ? null : price.amount().amount(), CURRENCY,
                price == null ? null : priceText(price),
                chosen.map(c -> c.recording().url()).orElse(null),
                chosen.map(CallEvidence::conversationUrl).orElse(null));
        SentReport report = new SentReport(subject, from, recipients.to(), recipients.cc(), body, null, fields,
                new SentReport.Tokens(usage.promptTokens(), usage.completionTokens(), usage.callSeconds()),
                transcripts, clock.instant(), attachments.files().stream()
                        .map(a -> new SentReport.AttachmentInfo(a.filename(), a.contentType(), a.content().length)).toList());
        return new Composed(email, report);
    }

    /** A minimal report that relies on as little as possible; used when {@link #compose} fails. */
    public Composed fallback(ReportInput in) {
        BookingTask t = in.task();
        StringBuilder body = new StringBuilder(HEADING).append("\n\n");
        body.append("Status: ").append(in.outcome()).append('\n');
        body.append("Stay: ").append(t.stay().checkIn()).append(" to ").append(t.stay().checkOut()).append('\n');
        if (in.hotel() != null) {
            body.append("Hotel Name: ").append(in.hotel().name()).append('\n');
            body.append("Hotel Address: ").append(orUnknown(in.hotel().address())).append('\n');
            body.append("Hotel Phone: ").append(in.hotel().bestPhone() == null ? UNKNOWN : in.hotel().bestPhone().e164()).append('\n');
        }
        if (in.bestOffer() != null) {
            body.append("Price for the stay: ").append(priceText(evaluator.price(in.bestOffer()).orElse(null))).append('\n');
        }
        body.append("\nSome report details could not be produced. No booking, reservation, hold or payment was made.\n\n")
                .append(signOff);
        Recipients recipients = recipients(t);
        Email email = new Email(recipients.to(), recipients.cc(), subject(false), body.toString(), List.of());
        SentReport report = new SentReport(subject(false), from, recipients.to(), recipients.cc(), body.toString(), null,
                null, null, List.of(), clock.instant(), List.of());
        return new Composed(email, report);
    }

    /**
     * The closing block: a dash line, the team name, then one line per member, "Name — email" or just
     * "Name". {@code members} is comma-separated "Name &lt;email&gt;" or "Name".
     */
    static String signOff(String teamName, String members) {
        StringBuilder s = new StringBuilder("—\n")
                .append(teamName == null || teamName.isBlank() ? DEFAULT_TEAM_NAME : teamName.strip()).append('\n');
        if (members != null) {
            for (String member : members.split(",")) {
                String m = member.strip();
                if (m.isEmpty()) continue;
                int lt = m.indexOf('<');
                int gt = m.indexOf('>', lt + 1);
                if (lt >= 0 && gt > lt) {
                    String name = m.substring(0, lt).strip();
                    String email = m.substring(lt + 1, gt).strip();
                    s.append(name.isEmpty() ? email : email.isEmpty() ? name : name + " — " + email).append('\n');
                } else {
                    s.append(m).append('\n');
                }
            }
        }
        return s.toString();
    }

    /**
     * {@code app.report.subject} when the report names an option whose availability the hotel
     * confirmed, else {@code app.report.subject-unconfirmed}. Wording is config-only; this is the one
     * place that chooses between them.
     */
    String subject(boolean confirmedOption) {
        return confirmedOption ? subject : subjectUnconfirmed;
    }

    /**
     * The address from the request is the recipient; {@code app.report.recipient}, when set, gets a
     * copy (once, if it is the same address). Pending the user's confirmation of CC versus override,
     * this is the one place to change it.
     */
    Recipients recipients(BookingTask task) {
        boolean copy = !reportRecipient.isEmpty() && !reportRecipient.equalsIgnoreCase(task.destinationEmail());
        return new Recipients(task.destinationEmail(), copy ? List.of(reportRecipient) : List.of());
    }

    /**
     * The intake's assumptions, plus what the chosen call settled: with no guest count from the guest,
     * the agent says "one adult" when a hotel insists, so the quote is for one adult.
     */
    private static List<String> assumptions(BookingTask t, Optional<CallEvidence> chosen) {
        List<String> all = new java.util.ArrayList<>(t.intake().assumptions());
        boolean agentSaidOneAdult = chosen.map(CallEvidence::transcript)
                .map(tr -> tr.turns().stream().anyMatch(turn -> turn.speaker() == TranscriptTurn.Speaker.AGENT
                        && turn.text().toLowerCase(java.util.Locale.ROOT).contains("one adult")))
                .orElse(false);
        if (!t.intake().isStated(TaskIntake.StatedField.ADULTS) && agentSaidOneAdult) {
            all.removeIf(a -> a.startsWith("Number of guests not given"));
            all.add("Guest count not given; hotel quoted for 1 adult");
        }
        return all;
    }

    /** The offer to report: the evaluator's pick (confirmed available, tiered price), or none. */
    private static Optional<CallEvidence> chosen(ReportInput in, ReportContext ctx) {
        if (in.bestOffer() == null) {
            return Optional.empty();
        }
        return ctx.calls().stream()
                .filter(c -> c.offer() != null && c.offer().id().equals(in.bestOffer().id()))
                .findFirst();
    }

    /** Never presents an estimate as a stated total. */
    static String priceText(OfferPrice price) {
        if (price == null) {
            return UNKNOWN;
        }
        if (price.estimated()) {
            return "≈ " + price.amount() + " incl. HST; municipal accommodation tax not confirmed "
                    + "(availability confirmed, not booked)";
        }
        return price.amount().amount().toPlainString() + " CAD all-in, availability confirmed, not booked";
    }

    private static String reference(HotelOffer offer) {
        return offer.enquiryId() == null || offer.enquiryId().isBlank() ? null : offer.enquiryId().strip();
    }

    /** One line per hotel called: available + price / sold out / voicemail / failed / ... */
    private String callSummary(CallEvidence c) {
        PhoneCall call = c.call();
        if (c.offer() != null && c.offer().availabilityConfirmed()) {
            return "available, " + evaluator.price(c.offer()).map(ReportComposer::priceText).orElse("no price given");
        }
        if (call.outcome() != null) {
            return switch (call.outcome()) {
                case NO_AVAILABILITY -> "sold out";
                case VOICEMAIL -> "voicemail";
                case TOO_EXPENSIVE -> "available but over budget";
                case IVR_FAILED -> "could not get past the phone menu";
                case HOLD_TIMEOUT -> "on hold too long";
                case WRONG_NUMBER -> "wrong number";
                case REFUSED_AI_CALLER -> "would not talk to an AI caller";
                case CALL_DROPPED -> "call dropped";
                case OFFER_RECEIVED -> "quoted, availability not confirmed";
                case UNKNOWN -> "no clear answer";
            };
        }
        return switch (call.status()) {
            case FAILED, NO_ANSWER, BUSY, CANCELLED -> "failed (" + (call.failureReason() == null
                    ? call.status().toString().toLowerCase().replace('_', ' ') : call.failureReason()) + ")";
            default -> "no result yet";
        };
    }

    private static String stayText(StayDetails stay) {
        return "%s to %s (%d night%s)".formatted(stay.checkIn(), stay.checkOut(), stay.nights(), stay.nights() == 1 ? "" : "s");
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? UNKNOWN : s;
    }
}
