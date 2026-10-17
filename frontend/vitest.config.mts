import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

// Unit tests for models, services and components (`npm test`). Runs in jsdom; `@/` maps to src/
// like tsconfig.json. E2E tests against the packaged app live in ../e2e (Playwright).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./vitest.setup.ts"],
    restoreMocks: true,
    unstubGlobals: true,
  },
});
