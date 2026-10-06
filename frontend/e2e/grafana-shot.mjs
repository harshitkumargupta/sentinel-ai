// Captures the provisioned Grafana dashboard (populated) to docs/screenshots/.
import puppeteer from 'puppeteer-core';
import { chromium } from 'playwright';

const GRAFANA = process.env.GRAFANA_URL || 'http://localhost:3000';
const USER = process.env.GRAFANA_USER || 'admin';
const PASS = process.env.GRAFANA_PASSWORD || 'admin';

const browser = await puppeteer.launch({ executablePath: chromium.executablePath(), headless: true,
  args: ['--window-size=1400,1800'] });
const page = await browser.newPage();
await page.setViewport({ width: 1400, height: 1800 });
await page.goto(`${GRAFANA}/login`, { waitUntil: 'networkidle2' });
try {
  await page.waitForSelector('input[name="user"]', { timeout: 8000 });
  await page.type('input[name="user"]', USER);
  await page.type('input[name="password"]', PASS);
  await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}), page.click('button[type="submit"]')]);
} catch { /* maybe already authed */ }
await page.goto(`${GRAFANA}/d/sentinel-overview/sentinelai-overview?from=now-15m&to=now&kiosk`, { waitUntil: 'networkidle2' });
await new Promise((r) => setTimeout(r, 5000));
await page.screenshot({ path: '../docs/screenshots/05-grafana.png', fullPage: true });
console.log('GRAFANA screenshot saved');
await browser.close();
