import { test, expect } from '@playwright/test';

// End-to-end smoke: login → dashboard → open incident → investigate → approve action.
// Credentials come from env (ADMIN_USER/ADMIN_PASS), defaulting to the dev seed admin.
const USER = process.env.ADMIN_USER || 'admin';
const PASS = process.env.ADMIN_PASS || 'Admin@123';

async function login(page) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(USER);
  await page.getByLabel('Password').fill(PASS);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/dashboard/);
  await expect(page.getByText('Command Center')).toBeVisible();
}

test('login → dashboard → incident → investigate → approve', async ({ page }) => {
  await login(page);

  // Open the first incident (if any) from the incidents list.
  await page.goto('/incidents');
  const firstIncident = page.locator('a[href^="/incidents/"]').first();
  if (await firstIncident.count()) {
    await firstIncident.click();
    await expect(page).toHaveURL(/incidents\/\d+/);
    // Investigate + approve buttons are role-gated; click when present.
    const investigate = page.getByRole('button', { name: /investigate/i });
    if (await investigate.count()) await investigate.first().click();
    const approve = page.getByRole('button', { name: /approve/i });
    if (await approve.count()) await approve.first().click();
  }
});

test('reduced-motion path: dashboard renders without animation', async ({ browser }) => {
  const context = await browser.newContext({ reducedMotion: 'reduce' });
  const page = await context.newPage();
  await login(page);
  // Under reduced motion, 3D is forced Off → the static threat orb (img) is present.
  await expect(page.getByRole('img', { name: /Threat level/i }).first()).toBeVisible();
  await context.close();
});

test('WebGL-off path: AttackGlobe falls back to the origins table', async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  // Force 3D quality Off before the app loads.
  await page.addInitScript(() => { try { localStorage.setItem('sentinel.gfxQuality', 'off'); } catch (e) { /* ignore */ } });
  await login(page);
  await expect(page.getByText('Attack origins')).toBeVisible();
});
