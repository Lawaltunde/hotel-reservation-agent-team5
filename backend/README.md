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
| `GET /api/tasks/{id}` | Task status, outcome, counts and offers (money: CAD numbers with 2 decimals, unknown = `null`). 404 if unknown |
| `GET /api/tasks/{id}/events` | SSE, event `progress` `{taskId, status, message, at}`: current status on connect, then each change; completes on a terminal status |
| `GET /api/municipalities?q=ott&limit=10` | `[{id, name, timeZone}]` (Ontario only) |
| `POST /webhooks/elevenlabs`, `POST /webhooks/resend` | 200 accepted or duplicate, 401 bad/missing signature |

Errors are RFC 9457 problem details: 400 has `errors: [...]`, 422 (city not an Ontario municipality,
or ambiguous) has `suggestions: [...]`.

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
| `OPENAI_API_KEY` | `not-set` | The app starts without it; transcript offer extraction then fails with a clear error. |
| `OPENAI_CHAT_MODEL` | `gpt-5.4-mini` | Needs Structured Outputs (JSON schema). |
| `OPENAI_REASONING_EFFORT` / `OPENAI_TEMPERATURE` | `none` / `0.1` | Sampling parameters are only accepted with reasoning effort `none`. |
| `hotel-agent.{elevenlabs,google-places,resend}.enabled` | `false` | `false` uses logging / no-op stubs; set `ELEVENLABS_ENABLED`, `GOOGLE_PLACES_ENABLED`, `RESEND_ENABLED` to `true` to call the real APIs. |
| `hotel-agent.twilio.*` | empty | Reserved for status callbacks; calls go through ElevenLabs' Twilio integration. |
| `hotel-agent.policy.*` | see `HotelAgentProperties` | Search radius, call limits, deadline reserve. Starter values, to be tuned. |
