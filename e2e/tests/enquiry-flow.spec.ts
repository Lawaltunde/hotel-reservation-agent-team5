import { expect, test, type Page } from "@playwright/test";

/**
 * Enquiry flow against the phase 2 contract:
 *   POST /api/tasks -> 202 {taskId, status}; GET /api/tasks/{id};
 *   SSE GET /api/tasks/{id}/events (event "progress"); GET /api/municipalities?q=;
 *   ProblemDetail 400/422 on invalid input.
 *
 * UI specs, marked test.fixme: the search form is being replaced by a chat UI backed by
 * POST /api/tasks/prompt; they will be rewritten against its labels when it lands.
 * API-level checks of the same flow run in tasks-api.spec.ts.
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

async function chooseCity(page: Page, city: string) {
  const cityField = page.getByLabel(/city/i);
  await cityField.fill(city);
  // The city field autocompletes from GET /api/municipalities; pick the suggestion if shown.
  const suggestion = page.getByRole("option", { name: new RegExp(`^${city}`, "i") }).first();
  if (await suggestion.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await suggestion.click();
  }
}

async function fillSearchForm(page: Page, city: string) {
  await chooseCity(page, city);
  await page.getByLabel(/check-?in/i).fill(CHECK_IN);
  await page.getByLabel(/check-?out/i).fill(CHECK_OUT);
  await page.getByLabel(/adults/i).fill("2");
  await page.getByLabel(/e-?mail/i).fill(EMAIL);
}

async function submitSearch(page: Page) {
  await page.getByRole("button", { name: /search|find|start|submit/i }).click();
}

test.describe("enquiry flow", () => {
  test.fixme("Ottawa enquiry runs to COMPLETED via the NO_CANDIDATES path", async ({ page }) => {
    await page.goto("/");
    await fillSearchForm(page, "Ottawa");

    const created = page.waitForResponse(
      (r) => r.url().endsWith("/api/tasks") && r.request().method() === "POST",
    );
    await submitSearch(page);

    const response = await created;
    expect(response.status()).toBe(202);
    const { taskId } = await response.json();
    expect(taskId).toBeTruthy();

    await expect(page).toHaveURL(new RegExp(`/task/?\\?id=${taskId}`));

    // Progress arrives over SSE; with an empty hotel index the task ends without candidates.
    await expect(page.getByText(/COMPLETED/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/no[_ ]candidates|no (suitable )?hotels/i).first()).toBeVisible();

    await expect(page.locator("body")).not.toContainText(DISHONEST_WORDING);

    // The API agrees with what the page shows.
    const task = await page.request.get(`/api/tasks/${taskId}`);
    expect(task.status()).toBe(200);
    expect(await task.json()).toMatchObject({ taskId, status: "COMPLETED" });
  });

  test.fixme("a city outside Ontario (Vancouver) is rejected with 422", async ({ page }) => {
    await page.goto("/");
    await fillSearchForm(page, "Vancouver");

    const rejected = page.waitForResponse(
      (r) => r.url().endsWith("/api/tasks") && r.request().method() === "POST",
    );
    await submitSearch(page);

    const response = await rejected;
    expect(response.status()).toBe(422);
    expect(response.headers()["content-type"]).toContain("application/problem+json");

    // The user stays on the form and sees why.
    await expect(page).not.toHaveURL(/\/task/);
    await expect(page.getByRole("alert").first()).toContainText(/ontario/i);
  });
});
