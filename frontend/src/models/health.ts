/**
 * Response body of `GET /api/health` on the backend.
 *
 * Example: `{"status":"UP","service":"hotel-reservation-agent","time":"2026-10-17T12:00:00Z","database":"UP"}`.
 * When the database is down the backend answers 503 with `status` and `database` set to "DOWN".
 */
export interface HealthStatus {
  /** "UP" when the backend is healthy. Kept open for other values the backend may add. */
  status: "UP" | (string & {});
  /** Name of the backend service. */
  service: string;
  /** Server time as an ISO-8601 instant. */
  time: string;
  /** "UP" or "DOWN"; absent on older backends. */
  database?: "UP" | "DOWN" | (string & {});
}
