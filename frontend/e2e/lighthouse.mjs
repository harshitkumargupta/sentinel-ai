// Authenticated Lighthouse run on the dashboard in Low 3D quality.
// Uses puppeteer-core (Lighthouse's flow API needs a Puppeteer page) driving the Chromium that
// Playwright already downloaded. Logs in, then runs a Lighthouse navigation on the same tab so the
// session's sessionStorage refresh token survives. Prints the category scores.
//   node e2e/lighthouse.mjs
import puppeteer from 'puppeteer-core';
import { startFlow } from 'lighthouse';
import { chromium } from 'playwright';
import fs from 'node:fs';

const BASE = process.env.BASE_URL || 'http://localhost:5173';
const USER = process.env.ADMIN_USER || 'admin';
const PASS = process.env.ADMIN_PASS || 'Admin@123';

const browser = await puppeteer.launch({ executablePath: chromium.executablePath(), headless: true });
const page = await browser.newPage();
await page.evaluateOnNewDocument(() => { try { localStorage.setItem('sentinel.gfxQuality', 'low'); } catch (e) { /* ignore */ } });

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' });
await page.waitForSelector('#username', { timeout: 15000 });
await page.type('#username', USER);
await page.type('#password', PASS);
await page.click('button[type="submit"]');
await page.waitForFunction(() => location.pathname.includes('dashboard'), { timeout: 20000 });
await new Promise((r) => setTimeout(r, 1500));

const flow = await startFlow(page, {
  config: {
    extends: 'lighthouse:default',
    settings: { onlyCategories: ['performance', 'accessibility', 'best-practices'], formFactor: 'desktop', screenEmulation: { disabled: true } },
  },
});
await flow.navigate(`${BASE}/dashboard`);
const result = await flow.createFlowResult();
const lhr = result.steps[0].lhr;
const score = (c) => Math.round((lhr.categories[c]?.score ?? 0) * 100);
const out = { performance: score('performance'), accessibility: score('accessibility'), bestPractices: score('best-practices') };
fs.writeFileSync('lighthouse-dashboard.json', JSON.stringify(lhr));
console.log('LIGHTHOUSE ' + JSON.stringify(out));
await browser.close();
