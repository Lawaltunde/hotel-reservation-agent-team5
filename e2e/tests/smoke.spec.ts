import { expect, test } from "@playwright/test";

test.describe("smoke", () => {
  test("home page shows the app name", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1, name: "Hotel Deal Agent" })).toBeVisible();
    await expect(page).toHaveTitle(/Hotel Deal Agent/i);
  });

  test("GET /api/health reports UP", async ({ request }) => {
    const response = await request.get("/api/health");
    expect(response.status()).toBe(200);
    expect(response.headers()["content-type"]).toContain("application/json");

    const body = await response.json();
    expect(body).toMatchObject({ status: "UP", service: "hotel-reservation-agent" });
    expect(Number.isNaN(Date.parse(body.time))).toBe(false);
  });

  test("backend status indicator says online", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("status").filter({ hasText: /^Backend:/ })).toHaveText(
      "Backend: online",
    );
  });
});
