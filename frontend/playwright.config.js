import { defineConfig, devices } from '@playwright/test';

// Playwright smoke + screenshot config. Run with: npx playwright test
// Requires browsers once: npx playwright install chromium
// BASE_URL points at a running app (default: the Vite dev server, which proxies /api to :8080).
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  // Optionally let Playwright start the dev server itself.
  webServer: process.env.PW_NO_SERVER ? undefined : {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
