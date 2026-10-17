import { expect, test, type Page } from "@playwright/test";

/**
 * The chat intake end to end: the user types one message, the backend reads it
 * (POST /api/tasks/prompt) and either starts a task or asks a follow-up question.
 *
 * Needs the app pointed at the mock prompt reader (support/mock-openai.mjs) and a fresh database:
 * the hotel index is empty and Places is off, so an Ottawa search ends COMPLETED with
 * NO_ELIGIBLE_HOTELS. The mock picks its reading by keyword: "ottawa", "seattle", "explode".
 */

const EMAIL = "e2e-traveller@example.com";

function composer(page: Page) {
  return page.getByRole("textbox", { name: "Message the hotel agent" });
}

async function say(page: Page, text: string) {
  await composer(page).fill(text);
  await page.getByRole("button", { name: "Send" }).click();
}

function taskIdFrom(page: Page): string {
  const id = new URL(page.url()).searchParams.get("task");
  expect(id, "task id in the URL").toBeTruthy();
  return id!;
}

test.describe("chat intake", () => {
  test("an Ottawa request runs to a finished search with the result email", async ({ page }) => {
    await page.goto("/");

    const created = page.waitForResponse((r) => r.url().endsWith("/api/tasks/prompt") && r.request().method() === "POST");
    await say(page, `cheapest hotel in Ottawa tomorrow for 2 nights, 2 adults, under $250 a night. Email ${EMAIL}`);
    expect((await created).status()).toBe(202);

    const understood = page.getByRole("region", { name: "What I understood" });
    await expect(understood).toBeVisible();
    await expect(understood).toContainText("Ottawa");
    await expect(understood).toContainText(EMAIL);
    await expect(page).toHaveURL(/[?&]task=/);

    await expect(page.getByText("This search is finished.")).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(`This is the email I sent to ${EMAIL}, exactly as it went out.`)).toBeVisible();
    await expect(page.getByRole("button", { name: "Start a new search" })).toBeVisible();
    await expect(composer(page)).toHaveCount(0);

    const task = await (await page.request.get(`/api/tasks/${taskIdFrom(page)}`)).json();
    expect(task).toMatchObject({ status: "COMPLETED", outcome: "NO_ELIGIBLE_HOTELS", city: "Ottawa" });
    expect(task.report.to).toBe(EMAIL);
    expect(task.report.bodyText).toContain("No confirmed available room was found");
    expect(task.report.bodyText).not.toMatch(/\bbooking confirmed\b|\breservation confirmed\b/i);
  });

  test("starting a new search brings the composer back", async ({ page }) => {
    await page.goto("/");
    await say(page, `Ottawa tomorrow, 2 adults, ${EMAIL}`);
    await expect(page.getByText("This search is finished.")).toBeVisible({ timeout: 60_000 });

    await page.getByRole("button", { name: "Start a new search" }).click();

    await expect(composer(page)).toBeVisible();
    await expect(composer(page)).toHaveValue("");
    await expect(page).not.toHaveURL(/[?&]task=/);
  });

  test("an out-of-scope city gets an answer, not a task", async ({ page }) => {
    await page.goto("/");

    const rejected = page.waitForResponse((r) => r.url().endsWith("/api/tasks/prompt") && r.request().method() === "POST");
    // Not in Canada, so out of scope whichever province set the backend supports.
    await say(page, `hotel in Seattle tomorrow for 2 nights, ${EMAIL}`);
    expect((await rejected).status()).toBe(422);

    await expect(page.getByText(/I can only search hotels in Canada\..*Which Canadian city should I search\?/)).toBeVisible();
    await expect(page).not.toHaveURL(/[?&]task=/);
    await expect(composer(page)).toBeEnabled();
  });

  test("a missing email is asked for, and the answer starts the search", async ({ page }) => {
    await page.goto("/");

    await say(page, "cheapest hotel in Ottawa tomorrow, 2 adults");
    await expect(page.getByText("What email address should I send the results to?")).toBeVisible();
    await expect(page).not.toHaveURL(/[?&]task=/);

    const created = page.waitForResponse((r) => r.url().endsWith("/api/tasks/prompt") && r.request().method() === "POST");
    await say(page, EMAIL);
    const response = await created;
    expect(response.status()).toBe(202);
    // The follow-up is joined with the first message, so the backend sees the whole request.
    expect(response.request().postDataJSON().prompt).toBe(`cheapest hotel in Ottawa tomorrow, 2 adults\n${EMAIL}`);

    await expect(page.getByRole("region", { name: "What I understood" })).toContainText(EMAIL);
  });

  test("when the request reader is down, nothing starts and the user can try again", async ({ page }) => {
    await page.goto("/");

    const failed = page.waitForResponse((r) => r.url().endsWith("/api/tasks/prompt") && r.request().method() === "POST");
    await say(page, `explode: Ottawa tomorrow, ${EMAIL}`);
    expect((await failed).status()).toBe(503);

    await expect(page.getByText(/Nothing has been started\./)).toBeVisible();
    await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
    await expect(page).not.toHaveURL(/[?&]task=/);
  });
});
