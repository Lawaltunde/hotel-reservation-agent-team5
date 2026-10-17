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

## Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8080` | |
| `app.cors.allowed-origins` | `http://localhost:3000` | Only needed when a browser calls the API cross-origin; the packaged UI is same-origin. |

API keys are listed in `/.env.example`.
