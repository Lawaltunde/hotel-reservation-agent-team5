import { expect, test, type Page } from "@playwright/test";

/**
 * Enquiry flow against the phase 2 contract:
 *   POST /api/tasks -> 202 {taskId, status}; GET /api/tasks/{id};
 *   SSE GET /api/tasks/{id}/events (event "progress"); GET /api/municipalities?q=;
 *   ProblemDetail 400/422 on invalid input.
 *
 * Needs a fresh database: the hotel index is empty until ingestion runs, so every Ontario
 * enquiry ends COMPLETED with outcome NO_ELIGIBLE_HOTELS (via NO_CANDIDATES). Email and voice
 * providers are the logging stubs, so nothing leaves the machine.
 */

/** ISO date (yyyy-mm-dd) `days` from today. */
function isoDateFromToday(days: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

const CHECK_IN = isoDateFromToday(30);
const CHECK_OUT = isoDateFromToday(32);
const EMAIL = "e2e-traveller@example.com";

/** Words the UI must never show for an enquiry-only task with no bookings. */
const DISHONEST_WORDING = /\b(booked|reservation confirmed|confirmation number)\b/i;

function apiTask(city: string, overrides: Record<string, unknown> = {}) {
  return {
    requestId: crypto.randomUUID(),
    city,
    checkIn: CHECK_IN,
    checkOut: CHECK_OUT,
    rooms: 1,
    adults: 2,
    children: 0,
    destinationEmail: EMAIL,
    deadline: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
    authorizationMode: "ENQUIRY_ONLY",
    ...overrides,
  };
}

async function fillSearchForm(page: Page, city: string, { pickSuggestion }: { pickSuggestion: boolean }) {
  const cityField = page.getByRole("combobox", { name: "City in Ontario" });
  await cityField.fill(city);
  if (pickSuggestion) {
    // Suggestions come from GET /api/municipalities (the seeded Ontario list).
    await page.getByRole("option", { name: new RegExp(`^${city}`) }).click();
    await expect(cityField).toHaveValue(city);
  }
  await page.getByLabel("Check-in").fill(CHECK_IN);
  await page.getByLabel("Check-out").fill(CHECK_OUT);
  await page.getByLabel("Adults", { exact: true }).fill("2");
  await page.getByLabel("Email", { exact: true }).fill(EMAIL);
}

async function submitSearch(page: Page) {
  await page.getByRole("button", { name: "Find my hotel" }).click();
}

test.describe("enquiry flow", () => {
  test("Ottawa enquiry runs to COMPLETED with no eligible hotels", async ({ page }) => {
    await page.goto("/");
    await fillSearchForm(page, "Ottawa", { pickSuggestion: true });

    const created = page.waitForResponse(
      (r) => r.url().endsWith("/api/tasks") && r.request().method() === "POST",
    );
    await submitSearch(page);

    const response = await created;
    expect(response.status()).toBe(202);
    const { taskId } = await response.json();
    expect(taskId).toBeTruthy();

    await expect(page).toHaveURL(new RegExp(`/task/?\\?id=${taskId}`));
    await expect(page.getByRole("heading", { level: 1, name: "Ottawa, Ontario" })).toBeVisible();

    // Progress arrives over SSE; with an empty hotel index the task ends without candidates.
    const result = page.getByRole("status").filter({ has: page.getByRole("heading", { name: /without a usable quote/ }) });
    await expect(result).toBeVisible({ timeout: 60_000 });
    await expect(result).toContainText("No eligible hotels");
    // The task usually finishes before the page subscribes, so the activity log may hold no
    // events; the step list still shows every step done.
    await expect(page.getByText("Done (done)")).toBeVisible();
    await expect(page.getByText("Finished", { exact: true })).toBeVisible();

    await expect(page.locator("body")).not.toContainText(DISHONEST_WORDING);

    // The API agrees with what the page shows.
    const task = await page.request.get(`/api/tasks/${taskId}`);
    expect(task.status()).toBe(200);
    expect(await task.json()).toMatchObject({
      taskId,
      status: "COMPLETED",
      outcome: "NO_ELIGIBLE_HOTELS",
      city: "Ottawa",
      callsPlaced: 0,
      bestOffer: null,
      offers: [],
    });
  });

  test("a city outside Ontario (Vancouver) is rejected with 422 in the UI", async ({ page }) => {
    await page.goto("/");
    await fillSearchForm(page, "Vancouver", { pickSuggestion: false });

    const rejected = page.waitForResponse(
      (r) => r.url().endsWith("/api/tasks") && r.request().method() === "POST",
    );
    await submitSearch(page);

    const response = await rejected;
    expect(response.status()).toBe(422);
    expect(response.headers()["content-type"]).toContain("application/problem+json");

    // The user stays on the form and sees why.
    await expect(page).not.toHaveURL(/\/task/);
    const alert = page.getByRole("alert").filter({ hasText: "We only search Ontario." });
    await expect(alert).toBeVisible();
    await expect(alert).toContainText("'Vancouver' is not a supported Ontario municipality");
    await expect(page.getByRole("combobox", { name: "City in Ontario" })).toHaveAttribute("aria-invalid", "true");
  });
});

test.describe("enquiry API", () => {
  test("rejects Vancouver with a ProblemDetail 422 and an empty suggestions list", async ({ request }) => {
    const response = await request.post("/api/tasks", { data: apiTask("Vancouver") });

    expect(response.status()).toBe(422);
    expect(response.headers()["content-type"]).toContain("application/problem+json");
    expect(await response.json()).toMatchObject({
      status: 422,
      title: "Location out of scope",
      detail: "'Vancouver' is not a supported Ontario municipality",
      suggestions: [],
    });
  });

  test("rejects a past check-in with a ProblemDetail 400 listing the errors", async ({ request }) => {
    const response = await request.post("/api/tasks", { data: apiTask("Ottawa", { checkIn: "2020-01-01" }) });

    expect(response.status()).toBe(400);
    const problem = await response.json();
    expect(problem).toMatchObject({ status: 400, title: "Invalid request" });
    expect(problem.errors).toContain("checkIn is in the past");
  });

  test("streams progress over SSE and ends the stream at COMPLETED", async ({ request }) => {
    const created = await request.post("/api/tasks", { data: apiTask("Ottawa") });
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

  test("autocomplete only returns Ontario municipalities", async ({ request }) => {
    const ottawa = await request.get("/api/municipalities?q=Ott");
    expect(ottawa.status()).toBe(200);
    expect(await ottawa.json()).toEqual([expect.objectContaining({ name: "Ottawa", timeZone: "America/Toronto" })]);

    const vancouver = await request.get("/api/municipalities?q=Vancouver");
    expect(await vancouver.json()).toEqual([]);
  });
});
