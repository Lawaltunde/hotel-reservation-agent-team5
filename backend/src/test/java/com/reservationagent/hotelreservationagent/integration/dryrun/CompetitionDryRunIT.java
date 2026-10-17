package com.reservationagent.hotelreservationagent.integration.dryrun;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.reservationagent.hotelreservationagent.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Competition dry run: the whole agent, end to end, with every paid provider replaced by WireMock.
 * <ol>
 *   <li>A messy one-shot prompt goes to {@code POST /api/tasks/prompt}. OpenAI is a WireMock stub: it
 *       returns the structured reading of the prompt, and later the offer extracted from each call.</li>
 *   <li>Google Places Text Search ("hotels in Ottawa, Ontario") returns four Ottawa hotels with phones;
 *       ElevenLabs accepts every outbound call.</li>
 *   <li>The test then plays ElevenLabs: as each call starts it posts a signed
 *       {@code post_call_transcription} webhook. Two hotels quote, one is sold out, one is voicemail.
 *       Calls run with the production concurrency and the finish-fast rule, so not every hotel need
 *       be called; the expected winner is computed from the hotels that were.</li>
 *   <li>Resend captures the email. Exactly one is sent, in the organizer's format: the cheapest hotel
 *       that confirmed availability, its address, the number called, the dates and the total, then
 *       the recording and conversation links and the transcripts. The same report is on
 *       {@code GET /api/tasks/{id}}, and its recording link serves the ElevenLabs audio.</li>
 * </ol>
 * Besides the full request, a prompt with no dates (tonight, one night, stated as an assumption) and
 * one with no email address (sent to the organizer inbox) run the same way.
 * The cheapest hotel is deliberately not the one with the cheapest room rate, and the sold-out
 * hotel mentions the lowest price of all, so the test fails if either is picked.
 *
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class CompetitionDryRunIT {

    private static final Logger log = LoggerFactory.getLogger(CompetitionDryRunIT.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private static final String WEBHOOK_SECRET = "wsec_dry_run_secret";
    private static final String RECIPIENT = "dryrun.traveller@example.com";
    /** REPORT_RECIPIENT: copied on every report, and the recipient when the prompt has no address. */
    private static final String ORGANIZER = "organizer.dryrun@example.com";
    private static final LocalDate TODAY = LocalDate.now(TORONTO);
    private static final LocalDate CHECK_IN = LocalDate.now(TORONTO).plusDays(1);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);
    private static final String PROMPT = ("hey!! need the cheapest hotel in ottawa ON tmrw for 2 nights, me + my wife "
            + "(2 adults) 1 room, max like $180/night all in. dont book anything just get quotes, email me at  "
            + RECIPIENT + "  thx");

    /** Everything after submission (calls, webhooks, extraction, email) must finish within this. */
    private static final Duration ELAPSED_BUDGET = Duration.ofSeconds(60);

    private static final String PUBLIC_BASE_URL = "https://dryrun.example.test";
    private static final byte[] AUDIO = "ID3 dry-run audio".getBytes(StandardCharsets.US_ASCII);

    static final WireMockServer providers = new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcClient jdbc;

    // ---------- the scenario ----------

    enum Result { QUOTE, SOLD_OUT, VOICEMAIL }

    record ScenarioHotel(String name, String address, String phone, double lat, double lon, int stars,
                         Result result, List<String[]> transcript, Map<String, Object> extracted) {
    }

    /** Taxes stated separately, every component known, so the Java comparable total is the sum. */
    private static Map<String, Object> quote(String room, String initial, String hst, String mat, String fees,
                                             String allIn, String roomType, String enquiryId) {
        Map<String, Object> e = baseExtraction("OFFER_RECEIVED");
        e.put("availabilityConfirmed", true);
        e.put("checkIn", CHECK_IN.toString());
        e.put("checkOut", CHECK_OUT.toString());
        e.put("roomType", roomType);
        e.put("guestCount", 2);
        e.put("currency", "CAD");
        e.put("roomSubtotal", new java.math.BigDecimal(room));
        e.put("hst", new java.math.BigDecimal(hst));
        e.put("municipalAccommodationTax", new java.math.BigDecimal(mat));
        e.put("mandatoryFees", new java.math.BigDecimal(fees));
        e.put("optionalFees", null);
        e.put("taxesIncludedInQuote", "EXCLUDED");
        e.put("initialQuotedTotal", new java.math.BigDecimal(initial));
        e.put("cancellationTerms", "Free cancellation until 6 p.m. on the day of arrival");
        e.put("depositTerms", "No deposit; pay at the property");
        e.put("enquiryId", enquiryId);
        e.put("allInTotal", new java.math.BigDecimal(allIn));
        e.put("quoteValidityText", "I can hold that rate for 24 hours");
        e.put("quoteValidityHours", new java.math.BigDecimal("24"));
        return e;
    }

    private static Map<String, Object> baseExtraction(String outcome) {
        Map<String, Object> e = new LinkedHashMap<>();
        for (String k : List.of("callOutcome", "availabilityConfirmed", "checkIn", "checkOut", "roomType", "guestCount",
                "currency", "roomSubtotal", "hst", "municipalAccommodationTax", "mandatoryFees", "optionalFees",
                "allInTotal", "taxesIncludedInQuote", "initialQuotedTotal", "cancellationTerms", "depositTerms",
                "enquiryId", "quoteValidityText", "quoteValidityHours", "roomHeld", "reservationCreated", "confirmationNumber", "writtenConfirmationPromised",
                "failureReason")) {
            e.put(k, null);
        }
        e.put("callOutcome", outcome);
        e.put("reservationCreated", false);
        return e;
    }

    private static String[] agent(String text) {
        return new String[] {"agent", text};
    }

    private static String[] hotel(String text) {
        return new String[] {"user", text};
    }

    private static final String DATES = CHECK_IN + " to " + CHECK_OUT;

    /** Cheapest room rate, but a mandatory fee makes it the dearer all-in total: 281.13. */
    static final ScenarioHotel RIDEAU = new ScenarioHotel("Rideau Gate Inn", "120 Rideau St, Ottawa, ON K1N 5X6",
            "+16135550111", 45.4270, -75.6890, 3, Result.QUOTE, List.of(
            agent("Hi, is this the Rideau Gate Inn? I'm an AI assistant calling for a guest about a room rate."),
            hotel("Yes, Rideau Gate Inn, how can I help?"),
            agent("Do you have one room for two adults, " + DATES + ", two nights?"),
            hotel("We do. A queen room is 214.50 for the two nights."),
            agent("Is there any better direct rate you could offer?"),
            hotel("I can do 189 for the two nights, plus HST of 24.57 and the city accommodation tax of 7.56."),
            agent("Are there any other mandatory fees?"),
            hotel("There is a 60 dollar mandatory amenity fee, so 281.13 all in. I can hold that rate for 24 hours. "
                    + "Your reference is RG-4471.")),
            quote("189.00", "214.50", "24.57", "7.56", "60.00", "281.13", "Queen room", "RG-4471"));

    /** The expected winner: 199 + 25.87 + 7.96 + 15.00 = 247.83 all in. */
    static final ScenarioHotel BYWARD = new ScenarioHotel("ByWard Suites", "55 ByWard Market Sq, Ottawa, ON K1N 9C3",
            "+16135550122", 45.4290, -75.6920, 3, Result.QUOTE, List.of(
            agent("Hello, is this ByWard Suites? I'm an AI assistant calling on behalf of a guest for a rate."),
            hotel("ByWard Suites, yes."),
            agent("Do you have a room for two adults, " + DATES + "?"),
            hotel("Yes, a king suite is 229 for both nights before tax."),
            agent("Is that your best direct rate? The guest is comparing a few hotels."),
            hotel("I can bring it to 199 for the two nights. HST is 25.87 and the municipal accommodation tax is 7.96."),
            agent("Any other mandatory fees?"),
            hotel("Just a 15 dollar cleaning fee, so 247.83 all in. I can hold that rate for 24 hours. "
                    + "The quote reference is BW-2208.")),
            quote("199.00", "229.00", "25.87", "7.96", "15.00", "247.83", "King suite", "BW-2208"));

    /** Mentions the lowest price of all, but has no room: must never win. */
    static final ScenarioHotel GLEBE = new ScenarioHotel("Glebe Budget Motel", "780 Bank St, Ottawa, ON K1S 3V6",
            "+16135550133", 45.4040, -75.6880, 2, Result.SOLD_OUT, List.of(
            agent("Hi, is this the Glebe Budget Motel? I'm an AI assistant asking about a room."),
            hotel("Yes."),
            agent("Do you have a room for two adults, " + DATES + "?"),
            hotel("Sorry, we're fully booked those nights. Normally it would be 129 a night.")),
            soldOut());

    static final ScenarioHotel PARLIAMENT = new ScenarioHotel("Parliament View Hotel", "1 Wellington St W, Ottawa, ON K1A 0A9",
            "+16135550144", 45.4230, -75.7000, 4, Result.VOICEMAIL, List.of(
            hotel("You've reached the Parliament View Hotel front desk. Please leave a message after the tone."),
            agent("Hello, this is an AI assistant calling about a room rate. I'll try again later, thank you.")),
            baseExtraction("VOICEMAIL"));

    private static Map<String, Object> soldOut() {
        Map<String, Object> e = baseExtraction("NO_AVAILABILITY");
        e.put("availabilityConfirmed", false);
        e.put("currency", "CAD");
        return e;
    }

    static final List<ScenarioHotel> HOTELS = List.of(RIDEAU, BYWARD, GLEBE, PARLIAMENT);

    // ---------- the requests ----------

    /** One prompt and what the agent must make of it. */
    record Request(String name, String prompt, String promptMarker, Map<String, Object> interpretation,
                   LocalDate checkIn, LocalDate checkOut, String to, List<String> cc, List<String> assumptions) {
    }

    /** The main case: tomorrow, 2 nights, 2 adults, a nightly budget, an email address. */
    static final Request FULL = new Request("full", PROMPT, "cheapest hotel in ottawa", promptParse(),
            CHECK_IN, CHECK_OUT, RECIPIENT, List.of(ORGANIZER), List.of());

    /** No dates at all: tonight for one night, and the email says so. */
    static final Request NO_DATES = new Request("no dates",
            "any cheap hotel in ottawa for 2 adults pls, results to " + RECIPIENT,
            "any cheap hotel in ottawa", interpretation(null, null, 2, null),
            TODAY, TODAY.plusDays(1), RECIPIENT, List.of(ORGANIZER),
            List.of("No dates given: asking hotels for tonight (" + TODAY + ")",
                    "No check-out or length of stay given: asking for 1 night"));

    /** No email address: the report goes to the organizer inbox, and the email says so. */
    static final Request NO_EMAIL = new Request("no email",
            "cheapest hotel ottawa tomorrow 2 nights 2 adults, no email sorry",
            "cheapest hotel ottawa tomorrow", interpretation("TOMORROW", 2, 2, null),
            CHECK_IN, CHECK_OUT, ORGANIZER, List.of(),
            List.of("No email address given: sending the report to the organizer inbox"));

    static final List<Request> REQUESTS = List.of(FULL, NO_DATES, NO_EMAIL);

    // ---------- wiring ----------

    @BeforeAll
    static void startProviders() {
        providers.start();
    }

    @AfterAll
    static void stopProviders() {
        providers.stop();
    }

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry r) {
        if (!providers.isRunning()) {
            providers.start();
        }
        String base = providers.baseUrl();
        r.add("hotel-agent.elevenlabs.enabled", () -> "true");
        r.add("hotel-agent.elevenlabs.base-url", () -> base);
        r.add("hotel-agent.elevenlabs.api-key", () -> "xi-dry-run");
        r.add("hotel-agent.elevenlabs.agent-id", () -> "agent_dry_run");
        r.add("hotel-agent.elevenlabs.agent-phone-number-id", () -> "phnum_dry_run");
        r.add("hotel-agent.elevenlabs.webhook-secret", () -> WEBHOOK_SECRET);
        r.add("hotel-agent.google-places.enabled", () -> "true");
        r.add("hotel-agent.google-places.base-url", () -> base);
        r.add("hotel-agent.google-places.api-key", () -> "goog-dry-run");
        r.add("hotel-agent.resend.enabled", () -> "true");
        r.add("hotel-agent.resend.base-url", () -> base);
        r.add("hotel-agent.resend.api-key", () -> "re_dry_run");
        r.add("hotel-agent.resend.from", () -> "Hotel Deal Agent <agent@example.com>");
        r.add("app.public-base-url", () -> PUBLIC_BASE_URL);
        r.add("app.report.recipient", () -> ORGANIZER);
        r.add("spring.ai.openai.base-url", () -> base + "/v1");
        r.add("spring.ai.openai.api-key", () -> "sk-dry-run");
        r.add("spring.ai.openai.chat.max-retries", () -> "0");
    }

    @BeforeEach
    void stubProviders() throws Exception {
        providers.resetAll();

        // Google Places: one place per hotel-name query (enrichment), all four for any other query (discovery).
        List<Map<String, Object>> places = HOTELS.stream().map(CompetitionDryRunIT::place).toList();
        providers.stubFor(post(urlPathMatching("/v1/places:search.*")).atPriority(10)
                .willReturn(okJson(JSON.writeValueAsString(Map.of("places", places)))));
        for (ScenarioHotel h : HOTELS) {
            providers.stubFor(post(urlPathMatching("/v1/places:search.*")).atPriority(1)
                    .withRequestBody(matchingJsonPath("$.textQuery", containing(h.name())))
                    .willReturn(okJson(JSON.writeValueAsString(Map.of("places", List.of(place(h)))))));
        }

        // ElevenLabs: every outbound call starts; the conversation id (unique, like the real one) is
        // derived from the task and the number dialled.
        providers.stubFor(post(urlEqualTo("/v1/convai/twilio/outbound-call"))
                .willReturn(okJson("""
                        {"success": true, "message": "Success",
                         "conversation_id": "conv_{{jsonPath request.body '$.conversation_initiation_client_data.dynamic_variables.task_id'}}_{{jsonPath request.body '$.to_number'}}",
                         "callSid": "CA{{randomValue length=32 type='HEXADECIMAL'}}"}
                        """).withTransformers("response-template")));

        // OpenAI: reading each prompt (POST /api/tasks/prompt). Offer extraction is stubbed per run.
        for (Request r : REQUESTS) {
            providers.stubFor(post(urlPathMatching(".*/chat/completions")).atPriority(2)
                    .withRequestBody(containing(r.promptMarker()))
                    .willReturn(okJson(chatCompletion(JSON.writeValueAsString(r.interpretation())))));
        }
        providers.stubFor(post(urlPathMatching(".*")).atPriority(100)
                .willReturn(aResponse().withStatus(599).withBody("dry run: no stub for this request")));

        // ElevenLabs: call audio behind the report's recording link.
        providers.stubFor(get(urlPathMatching("/v1/convai/conversations/.+/audio"))
                .willReturn(aResponse().withHeader("Content-Type", "audio/mpeg").withBody(AUDIO)));

        // Resend: accept and capture.
        providers.stubFor(post(urlEqualTo("/emails")).willReturn(okJson("{\"id\":\"{{randomValue type='UUID'}}\"}")
                .withTransformers("response-template")));
    }

    private static Map<String, Object> place(ScenarioHotel h) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", "places/dry-" + h.phone().substring(2));
        p.put("displayName", Map.of("text", h.name(), "languageCode", "en"));
        p.put("formattedAddress", h.address());
        p.put("internationalPhoneNumber", "+1 " + h.phone().substring(2, 5) + "-" + h.phone().substring(5, 8) + "-"
                + h.phone().substring(8));
        p.put("nationalPhoneNumber", "(" + h.phone().substring(2, 5) + ") " + h.phone().substring(5, 8) + "-"
                + h.phone().substring(8));
        p.put("businessStatus", "OPERATIONAL");
        p.put("location", Map.of("latitude", h.lat(), "longitude", h.lon()));
        p.put("types", List.of("hotel", "lodging"));
        p.put("primaryType", "hotel");
        p.put("rating", 4.1);
        p.put("websiteUri", "https://example.com/" + h.name().toLowerCase().replace(' ', '-'));
        return p;
    }

    /** The model's reading of {@link #PROMPT}: labels only, dates unresolved, email redacted. */
    private static Map<String, Object> promptParse() {
        return interpretation("TOMORROW", 2, 2, Map.of("amount", 180, "unit", "PER_NIGHT"));
    }

    /** An InterpretedPrompt for Ottawa, 1 room; every other field null (unstated). */
    private static Map<String, Object> interpretation(String checkInKind, Integer nights, Integer adults,
                                                      Map<String, Object> budget) {
        Map<String, Object> checkIn = null;
        if (checkInKind != null) {
            checkIn = new LinkedHashMap<>();
            for (String k : List.of("kind", "year", "month", "day", "weekday", "days")) {
                checkIn.put(k, null);
            }
            checkIn.put("kind", checkInKind);
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("city", "ottawa");
        p.put("checkIn", checkIn);
        p.put("checkOut", null);
        p.put("nights", nights);
        p.put("rooms", 1);
        p.put("adults", adults);
        p.put("children", null);
        p.put("budget", budget);
        p.put("amenities", List.of());
        p.put("notes", null);
        return p;
    }

    /** Offer extraction keyed on the hotel named in the transcript, quoting the dates the agent asked for. */
    private void stubExtraction(Request r) throws Exception {
        for (ScenarioHotel h : HOTELS) {
            Map<String, Object> extracted = new LinkedHashMap<>(h.extracted());
            if (extracted.get("checkIn") != null) {
                extracted.put("checkIn", r.checkIn().toString());
                extracted.put("checkOut", r.checkOut().toString());
            }
            providers.stubFor(post(urlPathMatching(".*/chat/completions")).atPriority(1)
                    .withRequestBody(containing(h.name()))
                    .willReturn(okJson(chatCompletion(JSON.writeValueAsString(extracted)))));
        }
    }

    private static String chatCompletion(String content) throws Exception {
        return JSON.writeValueAsString(Map.of(
                "id", "chatcmpl-dry-run",
                "object", "chat.completion",
                "created", Instant.now().getEpochSecond(),
                "model", "gpt-5.4-mini",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", content),
                        "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 400, "completion_tokens", 120, "total_tokens", 520)));
    }

    // ---------- the run ----------

    @Test
    void oneEmailWithTheCheapestConfirmedAvailableHotel() throws Exception {
        run(FULL, false);
    }

    /** ElevenLabs posts webhooks in parallel and retries them; still exactly one email. */
    @Test
    void concurrentAndRedeliveredWebhooksStillSendOneEmail() throws Exception {
        run(FULL, true);
    }

    @Test
    void aPromptWithNoDatesAsksForTonightForOneNightAndSaysSo() throws Exception {
        run(NO_DATES, false);
    }

    @Test
    void aPromptWithNoEmailIsReportedToTheOrganizerInbox() throws Exception {
        run(NO_EMAIL, false);
    }

    private void run(Request request, boolean concurrentWithRedelivery) throws Exception {
        stubExtraction(request);
        Instant submitted = Instant.now();
        String taskId = submit(request.prompt());
        log.info("dry run [{}]: task {} submitted", request.name(), taskId);

        // The email address never reaches the model.
        LoggedRequest interpretation = providers.findAll(postRequestedFor(urlPathMatching(".*/chat/completions"))
                .withRequestBody(containing(request.promptMarker()))).getFirst();
        assertThat(interpretation.getBodyAsString()).doesNotContain(RECIPIENT);
        if (request.prompt().contains(RECIPIENT)) {
            assertThat(interpretation.getBodyAsString()).contains("<email>");
        }

        // Play ElevenLabs: answer every call as it starts, until the report goes out.
        Map<String, String> answered = new LinkedHashMap<>();
        Instant firstCall = null;
        Instant giveUp = submitted.plus(ELAPSED_BUDGET);
        while (emailsFor(taskId).isEmpty()) {
            Map<String, String> started = conversationIds(taskId);
            started.keySet().removeAll(answered.keySet());
            if (!started.isEmpty() && firstCall == null) {
                firstCall = Instant.now();
            }
            List<ScenarioHotel> batch = started.keySet().stream().map(CompetitionDryRunIT::byPhone).toList();
            answer(started, batch, concurrentWithRedelivery);
            answered.putAll(started);
            if (Instant.now().isAfter(giveUp)) {
                await("report email", Duration.ZERO, () -> !emailsFor(taskId).isEmpty());
            }
            Thread.sleep(50);
        }
        Instant emailed = Instant.now();
        Thread.sleep(1500); // a second email would arrive right behind the first
        List<LoggedRequest> sent = emailsFor(taskId);
        assertThat(sent).as("exactly one report email").hasSize(1);

        Duration total = Duration.between(submitted, emailed);
        log.info("dry run: {} hotel(s) called; submit -> first call {} ms, total to email {} ms",
                answered.size(), firstCall == null ? -1 : Duration.between(submitted, firstCall).toMillis(),
                total.toMillis());
        assertThat(total).isLessThan(ELAPSED_BUDGET);

        List<ScenarioHotel> called = answered.keySet().stream().map(CompetitionDryRunIT::byPhone).toList();
        assertThat(called).as("each hotel is dialled at most once").doesNotHaveDuplicates();
        ScenarioHotel winner = called.stream().filter(h -> h.result() == Result.QUOTE)
                .min(java.util.Comparator.comparing(CompetitionDryRunIT::allInTotal))
                .orElseThrow(() -> new AssertionError("no quoting hotel was called: " + called));
        String winnerTotal = allInTotal(winner).toPlainString();
        String winnerConversation = answered.get(winner.phone());

        JsonNode email = JSON.readTree(sent.getFirst().getBodyAsString());
        String text = email.path("text").asString("") + "\n" + email.path("html").asString("");
        log.info("dry run email:\n{}", text);

        assertThat(email.path("to").toString()).contains(request.to());
        List<String> cc = new ArrayList<>();
        email.path("cc").forEach(a -> cc.add(a.asString()));
        assertThat(cc).as("cc").containsExactlyElementsOf(request.cc());
        long nights = java.time.temporal.ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());
        // A confirmed-available option gets the organizer's subject (the report itself says nothing was booked).
        assertThat(email.path("subject").asString()).isEqualTo("Team # 5 - Hotel Reservation Confirmed!");
        assertThat(text).contains(
                "Hotel Name: " + winner.name(),
                "Hotel Address: " + winner.address(),
                "Hotel Phone (number called): " + winner.phone(),
                request.checkIn() + " to " + request.checkOut() + " (" + nights + " night" + (nights == 1 ? "" : "s") + ")",
                "Price for the stay: " + winnerTotal + " CAD all-in, availability confirmed, not booked",
                "Reservation #: " + winner.extracted().get("enquiryId"));
        // The cheaper-room, sold-out and voicemail hotels may be listed, never chosen.
        for (ScenarioHotel other : HOTELS) {
            if (other != winner) {
                assertThat(text).doesNotContain("Hotel Name: " + other.name());
            }
        }
        assertThat(text).containsPattern("Call recording: " + java.util.regex.Pattern.quote(PUBLIC_BASE_URL) + "/\\S+")
                .contains("Conversation: https://elevenlabs.io/app/agents/history/" + winnerConversation)
                .contains("=== " + winner.name(), winner.transcript().get(5)[1])
                .containsPattern("Tokens spent: [1-9]\\d*");
        assertThat(text).doesNotContainIgnoringCase("reservation confirmed").doesNotContain("Booking confirmed");

        JsonNode task = JSON.readTree(client.get().uri("/api/tasks/{id}", taskId).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
        assertThat(task.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(task.path("outcome").asString()).isIn("ENQUIRY_COMPLETED", "QUOTE_RECEIVED");
        assertThat(task.path("bestOffer").path("hotelName").asString()).isEqualTo(winner.name());
        assertThat(task.path("bestOffer").path("negotiatedTotal").decimalValue()).isEqualByComparingTo(winnerTotal);

        JsonNode report = task.path("report");
        assertThat(report.path("bodyText").asString()).as("the API report is the email as sent")
                .isEqualTo(email.path("text").asString());
        assertThat(report.path("sentAt").isNull()).isFalse();
        JsonNode fields = report.path("fields");
        assertThat(fields.path("hotelName").asString()).isEqualTo(winner.name());
        assertThat(fields.path("hotelAddress").asString()).isEqualTo(winner.address());
        assertThat(fields.path("hotelPhone").asString()).isEqualTo(winner.phone());
        assertThat(fields.path("checkIn").asString()).isEqualTo(request.checkIn().toString());
        assertThat(fields.path("checkOut").asString()).isEqualTo(request.checkOut().toString());
        assertThat(report.path("to").asString()).isEqualTo(request.to());
        for (String assumption : request.assumptions()) {
            assertThat(text).as("assumption stated in the email").contains(assumption);
        }
        if (request.assumptions().isEmpty()) {
            assertThat(text).doesNotContain("Assumptions:");
        }
        assertThat(fields.path("price").decimalValue()).isEqualByComparingTo(winnerTotal);
        assertThat(fields.path("currency").asString()).isEqualTo("CAD");
        assertThat(report.path("transcripts").size()).isEqualTo(called.size());

        // The recording link in the email serves the call audio (fetched from ElevenLabs on demand).
        String recordingUrl = fields.path("recordingUrl").asString();
        assertThat(recordingUrl).startsWith(PUBLIC_BASE_URL + "/").isSubstringOf(text);
        var audio = client.get().uri(recordingUrl.substring(PUBLIC_BASE_URL.length())).exchange()
                .expectStatus().isOk().expectBody(byte[].class).returnResult();
        assertThat(audio.getResponseBody()).isEqualTo(AUDIO);
        // The conversation id is URL-encoded in the path ("+" becomes %2B). The report fetched the audio of
        // every answered call to attach it, and the link fetched the winner's again.
        assertThat(providers.findAll(getRequestedFor(urlPathMatching("/v1/convai/conversations/.+/audio"))))
                .extracting(r -> java.net.URLDecoder.decode(r.getUrl(), StandardCharsets.UTF_8))
                .contains("/v1/convai/conversations/" + winnerConversation + "/audio");

        // Recording and transcript are attached, the chosen hotel's call first.
        List<String> attached = new ArrayList<>();
        email.path("attachments").forEach(a -> attached.add(a.path("filename").asString()));
        assertThat(attached).as("attachments").isNotEmpty();
        assertThat(attached.get(0)).startsWith("call-1-").endsWith(".mp3");
        assertThat(attached.get(1)).isEqualTo(attached.get(0).replace(".mp3", "-transcript.txt"));
        assertThat(java.util.Base64.getDecoder().decode(email.path("attachments").get(0).path("content").asString()))
                .isEqualTo(AUDIO);
        assertThat(attached).allMatch(name -> name.matches("call-\\d+-[a-z0-9-]+(\\.mp3|-transcript\\.txt)"));
        List<String> reported = new ArrayList<>();
        report.path("attachments").forEach(a -> reported.add(a.asString()));
        assertThat(reported).as("report lists the attachment names").containsExactlyElementsOf(attached);
    }

    private void answer(Map<String, String> started, List<ScenarioHotel> batch, boolean concurrentWithRedelivery)
            throws Exception {
        if (!concurrentWithRedelivery) {
            for (ScenarioHotel h : batch) {
                postCallWebhook(started.get(h.phone()), h);
            }
            return;
        }
        List<ScenarioHotel> deliveries = new ArrayList<>(batch);
        deliveries.addAll(batch); // every webhook delivered twice, in parallel
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, deliveries.size()))) {
            List<java.util.concurrent.Future<?>> posted = new ArrayList<>();
            for (ScenarioHotel h : deliveries) {
                posted.add(pool.submit(() -> {
                    postCallWebhook(started.get(h.phone()), h);
                    return null;
                }));
            }
            for (var f : posted) {
                f.get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    private static ScenarioHotel byPhone(String phone) {
        return HOTELS.stream().filter(h -> h.phone().equals(phone)).findFirst()
                .orElseThrow(() -> new AssertionError("dialled an unknown number " + phone));
    }

    private static java.math.BigDecimal allInTotal(ScenarioHotel h) {
        Map<String, Object> e = h.extracted();
        return List.of("roomSubtotal", "hst", "municipalAccommodationTax", "mandatoryFees").stream()
                .map(k -> (java.math.BigDecimal) e.get(k))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    private String submit(String prompt) throws Exception {
        String body = client.post().uri("/api/tasks/prompt").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(JSON.writeValueAsString(Map.of("prompt", prompt)))
                .exchange().expectStatus().isAccepted().expectBody(String.class).returnResult().getResponseBody();
        return JSON.readTree(body).path("taskId").asString();
    }

    private Map<String, String> conversationIds(String taskId) {
        Map<String, String> byPhone = new LinkedHashMap<>();
        jdbc.sql("SELECT dialled_number, conversation_id FROM phone_calls WHERE task_id = :task AND conversation_id IS NOT NULL")
                .param("task", UUID.fromString(taskId))
                .query((rs, i) -> byPhone.put(rs.getString(1), rs.getString(2)))
                .list();
        return byPhone;
    }

    private void postCallWebhook(String conversationId, ScenarioHotel h) throws Exception {
        List<Map<String, Object>> turns = new ArrayList<>();
        int second = 0;
        for (String[] turn : h.transcript()) {
            turns.add(Map.of("role", turn[0], "message", turn[1], "time_in_call_secs", second));
            second += 7;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agent_id", "agent_dry_run");
        data.put("conversation_id", conversationId);
        data.put("status", "done");
        data.put("transcript", turns);
        data.put("metadata", Map.of("start_time_unix_secs", Instant.now().getEpochSecond() - second,
                "call_duration_secs", second, "phone_call",
                Map.of("direction", "outbound", "external_number", h.phone())));
        String body = JSON.writeValueAsString(Map.of(
                "type", "post_call_transcription", "event_timestamp", Instant.now().getEpochSecond(), "data", data));

        long t = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8)));

        client.post().uri("/webhooks/elevenlabs").contentType(MediaType.APPLICATION_JSON)
                .header("ElevenLabs-Signature", "t=" + t + ",v0=" + signature)
                .body(body)
                .exchange().expectStatus().isOk();
    }

    /** WireMock is reset per test, so every captured email belongs to this test's task. */
    private static List<LoggedRequest> emailsFor(String taskId) {
        return providers.findAll(postRequestedFor(urlEqualTo("/emails")));
    }

    private static void await(String what, Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant giveUp = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(giveUp)) {
                List<String> unmatched = providers.findAllUnmatchedRequests().stream()
                        .map(r -> r.getMethod() + " " + r.getUrl()).toList();
                throw new AssertionError("timed out after " + timeout + " waiting for " + what
                        + "; requests without a stub: " + unmatched + "; provider 599s: "
                        + providers.getAllServeEvents().stream()
                                .filter(e -> e.getResponse().getStatus() == 599)
                                .map(e -> e.getRequest().getUrl() + " " + abbreviate(e.getRequest().getBodyAsString()))
                                .toList());
            }
            Thread.sleep(100);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }
}
