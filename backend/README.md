# Backend

Spring Boot 4.1 / Java 25. See the root `README.md` for the architecture (§2) and
how to run the whole project (§19). Run Maven from the repository root.

```bash
./mvnw install -DskipTests            # once: builds the frontend into the backend's classpath
./mvnw -pl backend spring-boot:run    # http://localhost:8080 (UI + API)
./mvnw -pl backend verify             # backend tests only (needs the frontend artifact installed)
```

Spring Boot's Docker Compose support starts PostGIS from `compose.yaml`
(`imresamu/postgis:17-3.5`, db/user/password `hotelagent`) on a random host port and wires the
datasource. Flyway applies `src/main/resources/db/migration` on startup. Stop the database with
`docker compose down` in this folder.

## Endpoints

| Endpoint | Response |
|---|---|
| `GET /` | The Next.js UI (static export from the frontend module) |
| `GET /api/health` | `{"status":"UP","service":"hotel-reservation-agent","time":"<ISO-8601 instant>"}` |
| `GET /actuator/health` | Actuator health (includes the database) |
| `POST /api/tasks` | `202 {taskId, status}` + `Location`. Idempotent on `requestId`. 400 / 422 as ProblemDetail |
| `POST /api/tasks/prompt` | Body `{"prompt": "..."}` (1-2000 chars). `202 {taskId, status}` + `Location`. Optional `Idempotency-Key` header. 400 / 422 as above, 503 if the AI provider is unavailable |
| `GET /api/tasks/{id}` | Task status, outcome, `request` (see below), counts and offers (money: CAD numbers with 2 decimals, unknown = `null`). 404 if unknown |
| `GET /api/tasks/{id}/events` | SSE, event `progress` `{taskId, status, message, at}`: current status on connect, then each change; completes on a terminal status |
| `GET /api/recordings/{token}` | Call audio linked from the report email (proxied from ElevenLabs, not stored). 404 for an unknown or expired token, 502 if ElevenLabs fails |
| `GET /api/municipalities?q=ott&limit=10` | `[{id, name, timeZone}]` (Ontario only) |
| `POST /webhooks/elevenlabs`, `POST /webhooks/resend` | 200 accepted or duplicate, 401 bad/missing signature |

Errors are RFC 9457 problem details: 400 has `errors: [{field, message}]` (`field` is the JSON
property, or `null` for the whole request), 422 (city not an Ontario municipality, or ambiguous) has
`suggestions: [...]`.

### Prompt intake

`POST /api/tasks/prompt` reads one free-text request (one sentence or chat messages joined by
newlines; the latest wins) and submits it through the same validation as `POST /api/tasks`.
Nothing the prompt leaves out is presented as the user's: it is marked unstated and the voice
agent asks hotels neutrally.

- The email address is found in Java (the last one wins, lower-cased) and replaced with `<email>`
  before the prompt goes to OpenAI. With none, the report goes to `REPORT_RECIPIENT`; only if that
  is unset is it a 400 on `email`.
- The model describes dates ("next Friday", "Nov 20"); Java resolves them against today in
  America/Toronto. All no-date rules are in `IntakeDefaults.stay()`: no dates means tonight for one
  night (pending decision), no check-out means 1 night ("this/next weekend" means 2).
- Budgets come back as `{amount, unit}`; `IntakeDefaults.stayTotal()` multiplies PER_NIGHT by the
  nights and reads UNKNOWN as per night when under 600 for a stay of more than one night.
- A city not in `municipalities` is geocoded with Places (locality in ON; tolerates typos) and added.
- Deadline is now + `app.intake.default-deadline` (10 min); mode is always `ENQUIRY_ONLY`.
- 400 `field` values: `prompt`, `email`, `city`, `checkIn`, `checkOut`, `rooms`, `adults`,
  `children`, `maxBudget`, `Idempotency-Key`.

`request` in `GET /api/tasks/{id}`: `{city, checkIn, checkOut, adults, children, rooms, maxBudget,
maxBudgetPerNight, amenities[], notes, email, emailSource, originalPrompt, stated{...}, askedFor{...},
assumptions[]}`. Unstated values are `null` with `stated.<field> = false`; `askedFor` is what hotels
are asked.

`report` in `GET /api/tasks/{id}` (null until composed) is the stored copy of the email that was
sent: `{subject, from, to, cc[], sentAt, deliveryStatus, bodyText, bodyHtml, fields{hotelName,
hotelAddress, hotelPhone, reservationNumber, checkIn, checkOut, price, currency, priceNote,
recordingUrl, conversationUrl}, tokensSpent, tokens{...}, transcripts[]}`.

### Offer fields

Each entry of `offers` in `GET /api/tasks/{id}` is a phone quote, never a booking.

| Field | Meaning |
|---|---|
| `negotiatedTotal` | Comparable all-in total, computed in Java: room subtotal + HST + MAT + mandatory fees when all four are known; otherwise `allInTotal` if `taxInclusion` is `INCLUDED`; otherwise `null` (the offer cannot be called cheapest) |
| `roomSubtotal`, `hst`, `municipalAccommodationTax`, `mandatoryFees`, `optionalFees` | Components as the hotel stated them. Optional fees are never part of `negotiatedTotal` |
| `allInTotal` | The final total the hotel stated, as said on the call |
| `totalsDisagree` | `true` when the components sum to more than CA$1 away from `allInTotal`; show a warning |
| `quoteValidityText` | What the hotel said about how long the quote is valid, verbatim, or `null` |
| `quoteValidUntil` | Call end + the stated period in hours, or `null` when no period was given. Never produced by the model |

## Layers

`domain` (plain Java) ← `application` (use cases, ports, rules) ← `infrastructure` (JPA/JdbcClient
adapters, WebClient clients, Spring AI) and `presentation` (REST, SSE, webhooks). `config` holds
cross-cutting wiring (Clock, async, scheduling). `ArchitectureTest` enforces the rules.

With no hotels in the index yet (ingestion is a later phase), a task goes
`VALIDATING → DISCOVERING → NO_CANDIDATES → EMAIL_PENDING → COMPLETED` and the report is logged by
the email stub.

## Configuration

Local secrets are read from the root `.env` (`spring.config.import`); see `/.env.example`.
A variable that is present but empty falls back to the default below.

| Property / variable | Default | Notes |
|---|---|---|
| `server.port` | `8080` | |
| `app.cors.allowed-origins` | `http://localhost:3000` | Only needed when a browser calls the API cross-origin; the packaged UI is same-origin. |
| `OPENAI_API_KEY` | `not-set` | The app starts without it; offer extraction then fails with a clear error and prompt intake returns 503. |
| `app.intake.default-deadline` / `APP_INTAKE_DEFAULT_DEADLINE` | `PT10M` | Hard overall timeout of a prompt task: the best result so far is emailed just before it |
| `app.report.subject` / `APP_REPORT_SUBJECT` | `Team 5 - Hotel Reservation Enquiry Result` | Subject of the report email |
| `app.report.conversation-url-template` | `https://elevenlabs.io/app/conversational-ai/history/{conversationId}` | Dashboard link per call (path unverified) |
| `hotel-agent.policy.max-parallel-calls` / `HOTEL_AGENT_MAX_PARALLEL_CALLS` | `3` | Concurrent calls per task; keep within the ElevenLabs plan limit |
| `app.report.recipient` / `REPORT_RECIPIENT` | empty | Copied (CC) on every report; the request's email is always the recipient |
| `app.public-base-url` / `APP_PUBLIC_BASE_URL` | empty | Public URL of this backend. Without it the report says the recording link is unavailable |
| `app.report.recording-link-ttl` / `APP_REPORT_RECORDING_LINK_TTL` | `P30D` | How long an emailed recording link works |
| `OPENAI_CHAT_MODEL` | `gpt-5.4-mini` | Needs Structured Outputs (JSON schema). |
| `OPENAI_REASONING_EFFORT` / `OPENAI_TEMPERATURE` | `none` / `0.1` | Sampling parameters are only accepted with reasoning effort `none`. |
| `hotel-agent.{elevenlabs,google-places,resend}.enabled` | `false` | `false` uses logging / no-op stubs; set `ELEVENLABS_ENABLED`, `GOOGLE_PLACES_ENABLED`, `RESEND_ENABLED` to `true` to call the real APIs. |
| `hotel-agent.twilio.*` | empty | Reserved for status callbacks; calls go through ElevenLabs' Twilio integration. |
| `hotel-agent.policy.*` | see `HotelAgentProperties` | Search radius, call limits, deadline reserve. Starter values, to be tuned. |
