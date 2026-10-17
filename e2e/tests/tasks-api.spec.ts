import { expect, test } from "@playwright/test";

/**
 * API-level enquiry flow (POST /api/tasks, GET /api/tasks/{id}, SSE, autocomplete), independent
 * of the UI. Needs a fresh database: the hotel index is empty until ingestion runs, so every
 * in-scope enquiry ends COMPLETED with outcome NO_ELIGIBLE_HOTELS. Email and voice providers are
 * the logging stubs, so nothing leaves the machine.
 */

/** An in-scope city from the municipality seed. */
const IN_SCOPE_CITY = "Ottawa";
/**
 * A city outside the supported area, rejected with 422. Not Canadian, so it stays out of scope
 * when coverage widens from Ontario to all of Canada.
 */
const OUT_OF_SCOPE_CITY = "Seattle";

/** ISO date (yyyy-mm-dd) `days` from today. */
function isoDateFromToday(days: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

function taskRequest(city: string, overrides: Record<string, unknown> = {}) {
  return {
    requestId: crypto.randomUUID(),
    city,
    checkIn: isoDateFromToday(30),
    checkOut: isoDateFromToday(32),
    rooms: 1,
    adults: 2,
    children: 0,
    destinationEmail: "e2e-traveller@example.com",
    deadline: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
    authorizationMode: "ENQUIRY_ONLY",
    ...overrides,
  };
}

/** Text of one 400 `errors` entry. Kept in one place while the shape moves to {field, message}. */
function errorText(entry: unknown): string {
  if (typeof entry === "string") return entry;
  const e = entry as { field?: string; message?: string };
  return [e.field, e.message].filter(Boolean).join(" ");
}

test.describe("tasks API", () => {
  test("runs an in-scope enquiry to COMPLETED with no eligible hotels", async ({ request }) => {
    const created = await request.post("/api/tasks", { data: taskRequest(IN_SCOPE_CITY) });
    expect(created.status()).toBe(202);
    const { taskId } = await created.json();
    expect(created.headers()["location"]).toBe(`/api/tasks/${taskId}`);

    await expect
      .poll(async () => (await (await request.get(`/api/tasks/${taskId}`)).json()).status, { timeout: 30_000 })
      .toBe("COMPLETED");
    const task = await (await request.get(`/api/tasks/${taskId}`)).json();
    expect(task).toMatchObject({
      taskId,
      status: "COMPLETED",
      outcome: "NO_ELIGIBLE_HOTELS",
      city: IN_SCOPE_CITY,
      callsPlaced: 0,
      bestOffer: null,
      offers: [],
    });
  });

  test("resubmitting the same requestId returns the same task", async ({ request }) => {
    const body = taskRequest(IN_SCOPE_CITY);
    const first = await (await request.post("/api/tasks", { data: body })).json();
    const again = await request.post("/api/tasks", { data: body });
    expect(again.status()).toBe(202);
    expect((await again.json()).taskId).toBe(first.taskId);
  });

  test("rejects a city outside the supported area with a ProblemDetail 422", async ({ request }) => {
    const response = await request.post("/api/tasks", { data: taskRequest(OUT_OF_SCOPE_CITY) });

    expect(response.status()).toBe(422);
    expect(response.headers()["content-type"]).toContain("application/problem+json");
    const problem = await response.json();
    expect(problem).toMatchObject({ status: 422 });
    expect(problem.detail).toContain(OUT_OF_SCOPE_CITY);
    expect(Array.isArray(problem.suggestions)).toBe(true);
  });

  test("rejects a past check-in with a ProblemDetail 400 listing the errors", async ({ request }) => {
    const response = await request.post("/api/tasks", { data: taskRequest(IN_SCOPE_CITY, { checkIn: "2020-01-01" }) });

    expect(response.status()).toBe(400);
    expect(response.headers()["content-type"]).toContain("application/problem+json");
    const problem = await response.json();
    expect(problem).toMatchObject({ status: 400 });
    expect(problem.errors.map(errorText).join("; ")).toMatch(/checkIn.*past/);
  });

  test("streams progress over SSE and ends the stream at COMPLETED", async ({ request }) => {
    const created = await request.post("/api/tasks", { data: taskRequest(IN_SCOPE_CITY) });
    expect(created.status()).toBe(202);
    const { taskId } = await created.json();

    await expect
      .poll(async () => (await (await request.get(`/api/tasks/${taskId}`)).json()).status, { timeout: 30_000 })
      .toBe("COMPLETED");

    // A finished task sends one "progress" event with the current status, then closes the stream.
    const stream = await request.get(`/api/tasks/${taskId}/events`, { headers: { Accept: "text/event-stream" } });
    expect(stream.status()).toBe(200);
    expect(stream.headers()["content-type"]).toContain("text/event-stream");
    const body = await stream.text();
    expect(body).toContain("event:progress");
    expect(body).toContain('"status":"COMPLETED"');
  });

  test("autocomplete returns only supported municipalities", async ({ request }) => {
    const inScope = await request.get("/api/municipalities?q=Ott");
    expect(inScope.status()).toBe(200);
    expect(await inScope.json()).toContainEqual(expect.objectContaining({ name: IN_SCOPE_CITY, timeZone: "America/Toronto" }));

    const outOfScope = await request.get(`/api/municipalities?q=${OUT_OF_SCOPE_CITY}`);
    expect(await outOfScope.json()).toEqual([]);
  });
});
