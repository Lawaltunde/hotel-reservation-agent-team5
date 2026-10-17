# Competition run: runbook

One prompt in, one email out. The agent finds Ontario hotels (Google Places), calls them in
parallel (ElevenLabs + Twilio), confirms availability and the all-in price, and emails the cheapest
confirmed-available option. It never books, holds or pays.

## 1. Environment

Secrets live in the root `.env` (never commit or paste it). The app reads it automatically
(`spring.config.import`). Variables set on the command line override it.

| Variable | Value for the run | Notes |
|---|---|---|
| `OPENAI_API_KEY` | in `.env` | Prompt reading and offer extraction |
| `ELEVENLABS_API_KEY`, `ELEVENLABS_AGENT_ID`, `ELEVENLABS_AGENT_PHONE_NUMBER_ID` | in `.env` | |
| `ELEVENLABS_WEBHOOK_SECRET` | in `.env` | HMAC secret of the workspace post-call webhook |
| `GOOGLE_PLACES_API_KEY` | in `.env` | Hotel search and city geocoding |
| `ELEVENLABS_ENABLED` | `true` | **Pass at launch.** `false` = logging stub, no real calls |
| `GOOGLE_PLACES_ENABLED` | `true` | **Pass at launch.** Without it only 13 seeded cities work and no hotels are found |
| `APP_PUBLIC_BASE_URL` | the tunnel URL, e.g. `https://xxxx.trycloudflare.com` | Recording links in the email; no trailing slash |
| `REPORT_RECIPIENT` | the organizer inbox | CC on the report; also the To when the prompt has no email |
| `APP_EMAIL_TRANSPORT` | `smtp` (preferred) or `resend` | See the two variants below |
| `HOTEL_AGENT_MAX_PARALLEL_CALLS` | `3` | Must not exceed the ElevenLabs plan's concurrent-call limit |
| `APP_INTAKE_DEFAULT_DEADLINE` | `PT10M` | Hard timeout per task: the best result so far is emailed just before it |
| `APP_REPORT_SUBJECT` / `APP_REPORT_SUBJECT_UNCONFIRMED` | defaults: `Team # 5 - Hotel Reservation Confirmed!` / `Team # 5 - Hotel enquiry: no confirmed availability` | Change only if the organizer's wording differs |

Email, variant A: Gmail over SMTP (preferred):

```bash
APP_EMAIL_TRANSPORT=smtp
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=<gmail address>          # also the From address
SMTP_PASSWORD=<16-char app password>   # Google account needs 2-Step Verification
```

Email, variant B: Resend. Without a verified domain the sender is `onboarding@resend.dev`, and
Resend only delivers to the account owner. The CC is then dropped automatically and only the To is retried.

```bash
APP_EMAIL_TRANSPORT=resend
RESEND_ENABLED=true
RESEND_API_KEY=<in .env>
RESEND_FROM=onboarding@resend.dev      # or an address on a verified domain
```

## 2. Start (port 8080)

**For the run, start the self-contained fat jar, not `spring-boot:run` or IntelliJ.** The jar includes the
UI, so nothing outside it can change under the running app. **Do not build** (`./mvnw install`/`package`)
while it runs, and never `./mvnw install` into the shared `~/.m2`: running dev instances load the UI from
there. Build into a private repo with `-Dmaven.repo.local=...` if you must.

The jar does **not** start Postgres. Spring Boot's Docker Compose support is a dev-only dependency and is
left out of the jar. Start the database first and give the jar its URL:

```bash
# 1. Build the jar once, before the run (from the repo root; a private Maven repo keeps ~/.m2 untouched)
./mvnw -B -Dmaven.repo.local=/tmp/team5-m2 package -DskipTests
cp backend/target/hotel-reservation-agent-0.0.1-SNAPSHOT.jar /tmp/team5-agent.jar   # run this copy

# 2. Database: the same PostGIS as development, on a random host port
docker compose -f backend/compose.yaml up -d
DB_PORT=$(docker compose -f backend/compose.yaml port postgres 5432 | cut -d: -f2)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$DB_PORT/hotelagent
export SPRING_DATASOURCE_USERNAME=hotelagent SPRING_DATASOURCE_PASSWORD=hotelagent
```

```bash
# 3. Start FROM THE REPO ROOT (the jar reads ./.env for the API keys)

# Variant A (SMTP)
ELEVENLABS_ENABLED=true GOOGLE_PLACES_ENABLED=true \
APP_PUBLIC_BASE_URL=https://<tunnel> REPORT_RECIPIENT=<organizer inbox> \
APP_EMAIL_TRANSPORT=smtp SMTP_HOST=smtp.gmail.com SMTP_PORT=587 \
SMTP_USERNAME=<gmail> SMTP_PASSWORD='<app password>' \
java -jar /tmp/team5-agent.jar

# Variant B (Resend)
ELEVENLABS_ENABLED=true GOOGLE_PLACES_ENABLED=true \
APP_PUBLIC_BASE_URL=https://<tunnel> REPORT_RECIPIENT=<organizer inbox> \
APP_EMAIL_TRANSPORT=resend RESEND_ENABLED=true RESEND_FROM=onboarding@resend.dev \
java -jar /tmp/team5-agent.jar
```

Check the startup log for `apiKeyConfigured=true`. If it says the OpenAI key is not set, you did not start
from the repo root: the jar looks for `.env` in the current directory (or `../.env`).

Do not run a second instance (dev or jar) against the same database during the run. Each instance's
watchdog would also act on the other's tasks.

Development only: `./mvnw -pl backend spring-boot:run` starts Postgres automatically through Docker Compose.

UI and API are both on http://localhost:8080. Start the tunnel to 8080
(`cloudflared tunnel --url http://localhost:8080`). If cloudflared restarts the URL changes, and
**both** `APP_PUBLIC_BASE_URL` (restart the app) and the ElevenLabs webhook URL must be updated.

## 3. Pre-flight checklist (T-30 min)

- [ ] **App and DB up:** `curl -s localhost:8080/api/health` returns `200` with `"status":"UP","database":"UP"`.
      A `503`/`DOWN` means Postgres is down: check Docker.
- [ ] **Startup log:** `Offer extraction: model=… apiKeyConfigured=true`. There must be no `OPENAI_API_KEY is not set`.
- [ ] **Tunnel:** `curl -s https://<tunnel>/api/health` returns the same UP JSON from outside.
- [ ] **One URL everywhere:** the tunnel URL, `APP_PUBLIC_BASE_URL`, and the ElevenLabs workspace webhook
      (`https://<tunnel>/webhooks/elevenlabs`, post-call transcription, HMAC secret = `ELEVENLABS_WEBHOOK_SECRET`)
      all point to the same host.
- [ ] **Webhook reaches us:** after any test call, the log shows `task=… CALLING_FOR_QUOTES` progress and **no**
      `Rejected ELEVENLABS webhook with invalid or missing signature`. A rejection means the secret differs.
- [ ] **ElevenLabs agent:** the first message and system prompt match `docs/voice-agent-prompt.md` (the
      `{{stay_request}}` placeholder and the "final amount … including HST, the municipal accommodation tax and
      any fees" question). Max conversation duration is 180 s. Tools: End call, Play keypad touch tone.
- [ ] **Concurrency:** the ElevenLabs plan allows at least `HOTEL_AGENT_MAX_PARALLEL_CALLS` simultaneous calls.
- [ ] **Twilio geo-permissions:** Console → Voice → Settings → Geo permissions: **Canada** and **United States**
      enabled. If not, every call fails at once ("call start failed" in the log).
- [ ] **Test call to a team phone:** ElevenLabs dashboard → agent → phone number → outbound call to a team
      member's phone. Answer as a hotel ("Yes, a room is available, 189 all in, reference AB1"), then check
      the agent asks for availability first, then the final amount, and says it will confirm later.
- [ ] **Test email (fast, no real calls):** restart once with `ELEVENLABS_ENABLED=false GOOGLE_PLACES_ENABLED=false`
      (same email settings), then
      `curl -s -X POST localhost:8080/api/tasks/prompt -H 'Content-Type: application/json' -d '{"prompt":"hotel in Thunder Bay tonight"}'`.
      If the hotels table has no Thunder Bay hotels, the task ends at once (NO_CANDIDATES) and emails "no
      confirmed availability" to `REPORT_RECIPIENT`. If earlier runs stored hotels there, the stub "calls" them
      and the email comes after about 5.5 min. Check it lands, then **restart with both flags `true`**.
- [ ] **Clock:** the machine time is correct ("tonight" and "next Friday" are resolved in America/Toronto).

## 4. During the run

Submit the organizer's prompt exactly as given (UI chat, or
`POST /api/tasks/prompt {"prompt": "..."}`). Follow it in the UI, or with `GET /api/tasks/{id}`.

Expected log sequence (`grep task=<id>`):

1. `RECEIVED -> VALIDATING: request … accepted for <City>`. A 400 or 422 instead means the city or email
   could not be read (see the response `errors` / `suggestions`).
2. `live hotel search in <City>: found=N written=N`, then `screened=N eligible=M`. `found=0` means a Places
   key or quota problem.
3. `DISCOVERING -> RANKING -> CALLING_FOR_QUOTES`, and up to 3 calls start.
4. For each finished call, a post-call webhook arrives and the offer is extracted. When a batch ends with a
   confirmed priced offer, calling stops.
5. `CALLING_FOR_QUOTES -> OFFER_SELECTED: Best offer: CAD … (estimate)?`, then `-> EMAIL_PENDING -> COMPLETED:
   Report email accepted by provider`.

Warning signs:

| Log line | Meaning | Action |
|---|---|---|
| `call start failed: …` | ElevenLabs/Twilio refused the call (geo permission, number, credits) | Fix in the console; the task continues with other hotels |
| `Rejected ELEVENLABS webhook with invalid or missing signature` | Wrong webhook secret | Fix `ELEVENLABS_WEBHOOK_SECRET` or the webhook secret, then restart |
| No webhook ~3 min after a call starts | Webhook URL wrong or tunnel down | Check the tunnel and the webhook URL; the watchdog expires the call after 5.5 min |
| `Failed to process ELEVENLABS webhook … Offer extraction failed` / `Prompt could not be interpreted` | OpenAI key or model problem | Check `OPENAI_API_KEY` and `OPENAI_CHAT_MODEL` |
| `provider rejected the recipients …; retrying without cc` | Resend sandbox refused the CC | Expected on Resend without a domain; the To still gets the email |
| `report email failed: …` | Email transport failure | See section 5 |

## 5. If something goes wrong

- **A call hangs:** nothing to do. A call with no result after max-call-duration + 90 s (about 5.5 min) is
  marked FAILED and the task continues. The task deadline (10 min) always stops calling and emails the best
  confirmed result so far.
- **No hotel confirms:** the email still goes out with the "no confirmed availability" subject, listing every
  hotel called and its outcome.
- **The email fails** (task status `EMAIL_FAILED`; log `report email failed`):
  - The report is already stored: `GET /api/tasks/{id}` → `report.bodyText` holds the exact text, the
    subject is in `report.subject`, and the transcripts are in `report.transcripts`. Forward it manually as
    a last resort.
  - Fix the transport (Gmail app password, `SMTP_USERNAME`, or switch `APP_EMAIL_TRANSPORT`), restart, and
    submit the prompt again.
  - Gmail `535 Authentication failed` means the app password is wrong, or 2-Step Verification is off.
- **App restarts mid-run:** a task already in CALLING_FOR_QUOTES continues: its webhooks still arrive and the
  watchdog finishes it at the deadline. A task that was still searching (DISCOVERING/RANKING) is **not**
  resumed and ends DEADLINE_EXCEEDED without an email, so submit the prompt again.
- **The tunnel URL changed:** update `APP_PUBLIC_BASE_URL` (restart) and the ElevenLabs webhook URL,
  otherwise call results never arrive.

## 6. After the run

- Keep the app and tunnel up until the organizer has opened the recording links (they are served live from
  ElevenLabs through `/api/recordings/<token>` and expire after 30 days).
- `GET /api/tasks/{id}` shows the sent email, tokens spent, and the transcripts as evidence.
