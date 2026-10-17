# hotel-reservation-agent (backend)

Spring Boot 4.1 / Java 25 backend for the Hotel Deal Agent. See the root `README.md` for the architecture.

## Run locally

Requirements: JDK 25+, Docker running.

```bash
cd hotel-reservation-agent
./mvnw spring-boot:run
```

Spring Boot's Docker Compose support starts PostGIS from `compose.yaml` (`postgis/postgis:17-3.5`,
db/user/password `hotelagent`) on a random host port and wires the datasource automatically.
Flyway applies `src/main/resources/db/migration` on startup.

Stop the database afterwards with `docker compose down` in this folder.

## Endpoints

| Endpoint | Response |
|---|---|
| `GET /api/health` | `{"status":"UP","service":"hotel-reservation-agent","time":"<ISO-8601 instant>"}` |
| `GET /actuator/health` | Spring Boot Actuator health (includes DB) |
| `GET /actuator/info` | Actuator info |

## Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8080` | |
| `app.cors.allowed-origins` | `http://localhost:3000` | Comma-separated; env `APP_CORS_ALLOWED_ORIGINS`. Applies to `/api/**`. |

Future API keys are listed in `/.env.example`.

## Build

```bash
./mvnw -DskipTests package
```
