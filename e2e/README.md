# End-to-end tests (Playwright)

These tests drive the **packaged app**: one Spring Boot process that serves the Next.js UI at
`/` and the API at `/api/**`. They do not start a server. Point them at a running app with
`E2E_BASE_URL` (default `http://localhost:8080`). Only Chromium is configured.

| Spec | What it covers | State |
|---|---|---|
| `tests/smoke.spec.ts` | Home page title, `GET /api/health` is UP, backend indicator says "online" | runs |
| `tests/enquiry-flow.spec.ts` | Ottawa enquiry in the UI runs to `COMPLETED` / `NO_ELIGIBLE_HOTELS` with no "booked" wording; Vancouver → 422 in the UI and the API; 400 for a past check-in; SSE stream ends at `COMPLETED`; autocomplete is Ontario-only | runs (needs a fresh database, see below) |

## Run locally

Prerequisites: JDK 25+, Node 22+, Docker.

### 1. Start the app (from the repo root)

Option A: dev run. Spring Boot's Docker Compose support starts PostGIS from
`backend/compose.yaml`.

```bash
./mvnw -B -DskipTests install          # once, and after UI changes: puts the UI on the backend classpath
./mvnw -pl backend spring-boot:run     # http://localhost:8080
```

Option B: the packaged jar, which is what CI runs. `java -jar` does not start Compose, so
start the database yourself:

```bash
./mvnw -B -DskipTests package
docker compose -f backend/compose.yaml up -d
PORT=$(docker compose -f backend/compose.yaml port postgres 5432 | cut -d: -f2)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PORT/hotelagent \
SPRING_DATASOURCE_USERNAME=hotelagent SPRING_DATASOURCE_PASSWORD=hotelagent \
SPRING_DOCKER_COMPOSE_ENABLED=false \
java -jar backend/target/hotel-reservation-agent-0.0.1-SNAPSHOT.jar
```

The enquiry specs expect an **empty hotel index**, so every Ontario search ends with
`NO_ELIGIBLE_HOTELS`. Use a fresh database (CI's service container always is); with Compose,
`docker compose -f backend/compose.yaml down -v` before starting. Leave `ELEVENLABS_ENABLED`
and `RESEND_ENABLED` unset so no real calls or emails are made.

### 2. Run the tests (in another terminal)

```bash
cd e2e
npm ci
npx playwright install chromium     # first time only
npx playwright test                 # or: E2E_BASE_URL=http://localhost:9090 npx playwright test
```

Useful extras:

- `npx playwright test --headed` or `--ui` to watch the browser.
- `npx playwright show-report` opens the HTML report (`playwright-report/`). Traces,
  screenshots and videos of failures are in `test-results/`.
- `npm run typecheck` type-checks the specs.

## Conventions

- Use role- and label-based locators (`getByRole`, `getByLabel`, `getByText`). Do not use CSS selectors.
- Use the `request` fixture for API assertions so they share `baseURL` with the browser.
- Never put real secrets in specs. The app reads `OPENAI_API_KEY` and others from its own
  environment, not from these tests.

## CI

The `e2e` job in `.github/workflows/ci.yml` builds the jar and starts a PostGIS service
container (`imresamu/postgis:17-3.5`). It then runs the jar with `SPRING_DATASOURCE_*` and
`SPRING_DOCKER_COMPOSE_ENABLED=false`, waits for `/actuator/health`, and runs Playwright.
If the job fails, it uploads the Playwright report and test results as an artifact.
