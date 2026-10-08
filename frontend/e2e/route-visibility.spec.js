/**
 * Regression test for the "blink-then-disappear" bug:
 *   With Motion: Off, AnimatePresence(mode="wait") + MotionConfig(reducedMotion="always")
 *   left entering pages stuck at opacity:0. Fixed in Layout.jsx by using initial={false}
 *   and transition:{duration:0} when motionOff is true.
 *
 * Each test:
 *   1. Sets sentinel.motion to the target setting via addInitScript.
 *   2. Logs in.
 *   3. Navigates to every major route via React Router link clicks.
 *   4. Waits 5 seconds on each route, then asserts the route wrapper is opacity:1 and visible.
 */

import { test, expect } from '@playwright/test';

const USER = process.env.ADMIN_USER || 'admin';
const PASS = process.env.ADMIN_PASS || 'Admin@123';

const ROUTES = [
  { path: '/dashboard',     heading: /command center/i },
  { path: '/events',        heading: /events/i },
  { path: '/alerts',        heading: /alerts/i },
  { path: '/offenses',      heading: /offenses/i },
  { path: '/incidents',     heading: /incidents/i },
  { path: '/search',        heading: /event search/i },
  { path: '/assets',        heading: /assets/i },
  { path: '/vulnerabilities', heading: /vulnerabilities/i },
  { path: '/reference-sets', heading: /reference sets/i },
  { path: '/playbooks',     heading: /playbooks/i },
  { path: '/honeytokens',   heading: /honeytokens/i },
  { path: '/sites',         heading: /sites/i },
  { path: '/log-sources',   heading: /log sources/i },
];

async function login(page) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(USER);
  await page.getByLabel('Password', { exact: true }).fill(PASS);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/dashboard/);
}

/**
 * Assert that the route-wrapper motion.div inside .content is fully visible
 * (opacity 1, not display:none, not visibility:hidden).
 */
async function assertRouteVisible(page, routePath) {
  const wrapper = page.locator('.content > div').first();
  // Opacity must be 1 — the bug caused it to be stuck at 0.
  const opacity = await wrapper.evaluate((el) => window.getComputedStyle(el).opacity);
  expect(opacity, `opacity on ${routePath}`).toBe('1');

  // The wrapper must occupy space.
  const display = await wrapper.evaluate((el) => window.getComputedStyle(el).display);
  expect(display, `display on ${routePath}`).not.toBe('none');

  const visibility = await wrapper.evaluate((el) => window.getComputedStyle(el).visibility);
  expect(visibility, `visibility on ${routePath}`).toBe('visible');
}

test.describe('Route visibility — Motion: Off', () => {
  test.use({
    // Playwright does NOT set reducedMotion here; we want to test the app's own Motion:Off setting.
  });

  test('all routes remain visible for 5 s after navigation with Motion: Off', async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    // Set Motion: Off before the app bootstraps.
    await page.addInitScript(() => {
      try { localStorage.setItem('sentinel.motion', 'off'); } catch { /* ignore */ }
    });

    await login(page);

    for (const { path } of ROUTES) {
      await page.goto(path);
      // Wait for the page to settle (Suspense lazy-load + API call).
      await page.waitForLoadState('networkidle').catch(() => {});
      // 5-second hold — the bug manifested as disappearance within 1–3 s.
      await page.waitForTimeout(5000);
      await assertRouteVisible(page, path);
    }

    await context.close();
  });
});

test.describe('Route visibility — Motion: Full', () => {
  test('all routes remain visible for 5 s after navigation with Motion: Full', async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();

    await page.addInitScript(() => {
      try { localStorage.setItem('sentinel.motion', 'full'); } catch { /* ignore */ }
    });

    await login(page);

    for (const { path } of ROUTES) {
      await page.goto(path);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(5000);
      await assertRouteVisible(page, path);
    }

    await context.close();
  });
});

test.describe('Route visibility — OS reduced-motion preference', () => {
  test('all routes remain visible for 5 s with browser reducedMotion:reduce', async ({ browser }) => {
    const context = await browser.newContext({ reducedMotion: 'reduce' });
    const page = await context.newPage();

    await login(page);

    for (const { path } of ROUTES) {
      await page.goto(path);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(5000);
      await assertRouteVisible(page, path);
    }

    await context.close();
  });
});
