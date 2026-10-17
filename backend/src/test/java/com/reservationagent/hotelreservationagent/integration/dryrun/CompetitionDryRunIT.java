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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
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
 *   <li>A messy one-shot prompt goes to {@code POST /api/tasks/prompt} (falls back to the structured
 *       {@code POST /api/tasks} while the prompt endpoint is not merged).</li>
 *   <li>Google Places returns four Ottawa hotels with phones; ElevenLabs accepts every outbound call.</li>
 *   <li>The test then plays ElevenLabs: it posts a signed {@code post_call_transcription} webhook per
 *       call. Two hotels quote, one is sold out, one is voicemail. OpenAI (offer extraction) is a
 *       WireMock stub keyed on each transcript.</li>
 *   <li>Resend captures the email. Exactly one is sent, and it names the cheapest hotel that
 *       confirmed availability, with its address, phone, dates, all-in total and transcript.</li>
 * </ol>
 * The cheapest hotel is deliberately not the one with the cheapest room rate, and the sold-out
 * hotel mentions the lowest price of all, so the test fails if either is picked.
 *
 * <p>Contracts still moving (prompt endpoint, Places discovery, report shape, email links) are
 * isolated in the constants and the {@code PENDING_*} switches below.
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
    private static final LocalDate CHECK_IN = LocalDate.now(TORONTO).plusDays(1);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);
    private static final String PROMPT = ("hey!! need the cheapest hotel in ottawa ON tmrw for 2 nights, me + my wife "
            + "(2 adults) 1 room, max like $180/night all in. dont book anything just get quotes, email me at  "
            + RECIPIENT + "  thx");

    /** Everything after submission (calls, webhooks, extraction, email) must finish within this. */
    private static final Duration ELAPSED_BUDGET = Duration.ofSeconds(60);

    /** Flip when the backend merges each piece (see the class comment). */
    private static final boolean PENDING_EMAIL_LINKS = true;
    /** While Places discovery is not merged, the hotels must also be in the local index. */
    private static final boolean SEED_HOTEL_INDEX = true;

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
    static final ScenarioHotel CHEAPEST_AVAILABLE = BYWARD;
    static final String CHEAPEST_TOTAL = "247.83";

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
        r.add("hotel-agent.policy.max-parallel-calls", () -> "4");
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

        // OpenAI: offer extraction, keyed on the hotel named in the transcript.
        for (ScenarioHotel h : HOTELS) {
            providers.stubFor(post(urlPathMatching(".*/chat/completions")).atPriority(1)
                    .withRequestBody(containing(h.name()))
                    .willReturn(okJson(chatCompletion(JSON.writeValueAsString(h.extracted())))));
        }
        // OpenAI: prompt parsing (POST /api/tasks/prompt). TODO(contract): match the backend's schema.
        providers.stubFor(post(urlPathMatching(".*/chat/completions")).atPriority(2)
                .withRequestBody(containing("cheapest hotel in ottawa"))
                .willReturn(okJson(chatCompletion(JSON.writeValueAsString(promptParse())))));
        providers.stubFor(post(urlPathMatching(".*")).atPriority(100)
                .willReturn(aResponse().withStatus(599).withBody("dry run: no stub for this request")));

        // Resend: accept and capture.
        providers.stubFor(post(urlEqualTo("/emails")).willReturn(okJson("{\"id\":\"{{randomValue type='UUID'}}\"}")
                .withTransformers("response-template")));

        if (SEED_HOTEL_INDEX) {
            seedHotelIndex();
        }
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

    /** Best guess at the prompt parser's output; kept in one place until the contract is final. */
    private static Map<String, Object> promptParse() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("city", "Ottawa");
        p.put("checkIn", CHECK_IN.toString());
        p.put("checkOut", CHECK_OUT.toString());
        p.put("rooms", 1);
        p.put("adults", 2);
        p.put("children", 0);
        p.put("maxNightlyBudgetCad", 180);
        p.put("authorizationMode", "ENQUIRY_ONLY");
        return p;
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

    private void seedHotelIndex() {
        UUID ottawa = jdbc.sql("SELECT id FROM municipalities WHERE name = 'Ottawa'").query(UUID.class).single();
        for (ScenarioHotel h : HOTELS) {
            jdbc.sql("""
                            INSERT INTO hotels (id, canonical_key, name, address, municipality_id, location, property_type,
                                stars, price_tier, phones, phone_confidence, operational, last_seen_at)
                            VALUES (:id, :key, :name, :address, :municipality, ST_MakePoint(:lon, :lat)::geography, 'HOTEL',
                                :stars, 'UNKNOWN', ARRAY[:phone], 0.9, true, :now)
                            ON CONFLICT (canonical_key) DO NOTHING
                            """)
                    .param("id", UUID.randomUUID()).param("key", "dryrun:" + h.phone()).param("name", h.name())
                    .param("address", h.address()).param("municipality", ottawa).param("lon", h.lon())
                    .param("lat", h.lat()).param("stars", h.stars()).param("phone", h.phone())
                    .param("now", Timestamp.from(Instant.now())).update();
        }
    }

    // ---------- the run ----------

    @Test
    void oneEmailWithTheCheapestConfirmedAvailableHotel() throws Exception {
        run(false);
    }

    /** ElevenLabs posts webhooks in parallel and retries them; still exactly one email. */
    @Test
    void concurrentAndRedeliveredWebhooksStillSendOneEmail() throws Exception {
        run(true);
    }

    private void run(boolean concurrentWithRedelivery) throws Exception {
        Instant submitted = Instant.now();
        String taskId = submit();
        log.info("dry run: task {} submitted", taskId);

        // Every hotel is dialled once; wait until each call has its conversation id.
        await("4 outbound calls", Duration.ofSeconds(30), () -> conversationIds(taskId).size() == HOTELS.size());
        Map<String, String> conversations = conversationIds(taskId);
        assertThat(conversations.keySet()).containsExactlyInAnyOrderElementsOf(HOTELS.stream().map(ScenarioHotel::phone).toList());
        assertThat(providers.findAll(postRequestedFor(urlEqualTo("/v1/convai/twilio/outbound-call")))).hasSize(HOTELS.size());
        Instant callsStarted = Instant.now();

        if (concurrentWithRedelivery) {
            List<ScenarioHotel> deliveries = new ArrayList<>(HOTELS);
            deliveries.addAll(HOTELS); // every webhook delivered twice
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(deliveries.size())) {
                List<java.util.concurrent.Future<?>> posted = new ArrayList<>();
                for (ScenarioHotel h : deliveries) {
                    posted.add(pool.submit(() -> {
                        postCallWebhook(conversations.get(h.phone()), h);
                        return null;
                    }));
                }
                for (var f : posted) {
                    f.get(60, java.util.concurrent.TimeUnit.SECONDS);
                }
            }
        } else {
            for (ScenarioHotel h : HOTELS) {
                postCallWebhook(conversations.get(h.phone()), h);
            }
        }

        await("report email", ELAPSED_BUDGET, () -> !emailsFor(taskId).isEmpty());
        Instant emailed = Instant.now();
        Thread.sleep(1500); // a second email would arrive right behind the first
        List<LoggedRequest> sent = emailsFor(taskId);
        assertThat(sent).as("exactly one report email").hasSize(1);

        Duration total = Duration.between(submitted, emailed);
        log.info("dry run: submit -> calls started {} ms, calls -> email {} ms, total {} ms",
                Duration.between(submitted, callsStarted).toMillis(), Duration.between(callsStarted, emailed).toMillis(),
                total.toMillis());
        assertThat(total).isLessThan(ELAPSED_BUDGET);

        JsonNode email = JSON.readTree(sent.getFirst().getBodyAsString());
        String text = email.path("text").asString("") + "\n" + email.path("html").asString("");
        String attachments = decodedAttachments(email);
        log.info("dry run email:\n{}", text);

        assertThat(email.path("to").toString()).contains(RECIPIENT);
        assertThat(text).contains(CHEAPEST_AVAILABLE.name(), CHEAPEST_AVAILABLE.address(), CHEAPEST_TOTAL)
                .contains(CHECK_IN.toString(), CHECK_OUT.toString());
        assertThat(text.contains(CHEAPEST_AVAILABLE.phone()) || text.contains("613-555-0122")
                || text.contains("(613) 555-0122")).as("winner's phone number in the email").isTrue();
        // The winner comes first; the cheaper-room and sold-out hotels never head the report.
        int winnerAt = text.indexOf(CHEAPEST_AVAILABLE.name());
        for (ScenarioHotel other : List.of(RIDEAU, GLEBE, PARLIAMENT)) {
            int at = text.indexOf(other.name());
            assertThat(at == -1 || at > winnerAt).as("%s mentioned before the winner", other.name()).isTrue();
        }
        assertThat(text).doesNotContainIgnoringCase("reservation confirmed").doesNotContain("Booking confirmed");
        assertThat(text + attachments).as("the winner's call transcript")
                .contains("I can bring it to 199 for the two nights");
        if (!PENDING_EMAIL_LINKS) {
            assertThat(text).containsPattern("https?://\\S+");
        }

        JsonNode task = JSON.readTree(client.get().uri("/api/tasks/{id}", taskId).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
        assertThat(task.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(task.path("outcome").asString()).isIn("ENQUIRY_COMPLETED", "QUOTE_RECEIVED");
        assertThat(task.path("bestOffer").path("hotelName").asString()).isEqualTo(CHEAPEST_AVAILABLE.name());
        assertThat(task.path("bestOffer").path("negotiatedTotal").decimalValue()).isEqualByComparingTo(CHEAPEST_TOTAL);
        assertThat(task.path("offers").size()).as("offers from the two available hotels").isEqualTo(2);
        if (task.has("report") && !task.path("report").isNull()) {
            assertThat(task.path("report").toString()).contains(CHEAPEST_AVAILABLE.name());
        }
    }

    /** Prompt endpoint if present; otherwise the structured request the prompt should produce. */
    private String submit() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        var prompt = client.post().uri("/api/tasks/prompt").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(JSON.writeValueAsString(Map.of("prompt", PROMPT)))
                .exchange().expectBody(String.class).returnResult();
        int status = prompt.getStatus().value();
        if (status == 202 || status == 200 || status == 201) {
            return JSON.readTree(prompt.getResponseBody()).path("taskId").asString();
        }
        assertThat(status).as("prompt endpoint missing (404/405) or failing: %s", prompt.getResponseBody()).isIn(404, 405);
        log.warn("dry run: POST /api/tasks/prompt not available ({}); using the structured POST /api/tasks", status);
        Map<String, Object> request = new LinkedHashMap<>(promptParse());
        request.remove("maxNightlyBudgetCad");
        request.put("requestId", idempotencyKey);
        request.put("destinationEmail", RECIPIENT);
        request.put("deadline", Instant.now().plus(Duration.ofHours(2)).toString());
        request.put("maximumTotalCad", 360);
        String body = client.post().uri("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .body(JSON.writeValueAsString(request))
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
        data.put("metadata", Map.of("call_duration_secs", second, "phone_call",
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

    private static String decodedAttachments(JsonNode email) {
        StringBuilder all = new StringBuilder();
        for (JsonNode a : email.path("attachments")) {
            try {
                all.append(new String(Base64.getDecoder().decode(a.path("content").asString("")), StandardCharsets.UTF_8))
                        .append('\n');
            } catch (IllegalArgumentException e) {
                all.append(a.path("content").asString("")).append('\n');
            }
        }
        return all.toString();
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
