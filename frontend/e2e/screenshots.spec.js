import { test } from '@playwright/test';

// Generates the report/demo screenshots into docs/screenshots/. Run:
//   npx playwright test e2e/screenshots.spec.js
const USER = process.env.ADMIN_USER || 'admin';
const PASS = process.env.ADMIN_PASS || 'Admin@12345';
const OUT = '../docs/screenshots';

async function login(page) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(USER);
  await page.getByLabel('Password').fill(PASS);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/dashboard/);
}

test('capture login', async ({ page }) => {
  await page.goto('/login');
  await page.waitForTimeout(600);
  await page.screenshot({ path: `${OUT}/01-login.png`, fullPage: true });
});

test('capture dashboard (command center + globe)', async ({ page }) => {
  await login(page);
  await page.waitForTimeout(1500); // let the globe + counters settle
  await page.screenshot({ path: `${OUT}/02-dashboard.png`, fullPage: true });
});

test('capture dashboard light theme', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: /toggle theme/i }).click();
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${OUT}/03-dashboard-light.png`, fullPage: true });
});

test('capture command palette', async ({ page }) => {
  await login(page);
  await page.keyboard.press('Meta+k');
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${OUT}/04-command-palette.png` });
});
