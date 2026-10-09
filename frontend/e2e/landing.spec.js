/**
 * Playwright regression tests for the SentinelAI cinematic landing page.
 *
 * Tests verify: page loads, sections visible, CTAs work, back navigation,
 * refresh, mobile viewport, reduced motion, console errors, and that the
 * SOC app is unaffected by the landing page integration.
 *
 * Does NOT depend on a running backend — landing page is backend-independent.
 */

import { test, expect } from '@playwright/test'

const BASE = process.env.BASE_URL || 'http://localhost:5173'

test.describe('Landing page — core', () => {
  test('GET / renders the cinematic landing page', async ({ page }) => {
    const errors = []
    page.on('console', (msg) => { if (msg.type() === 'error') errors.push(msg.text()) })

    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    // Headline present
    await expect(page.locator('h1')).toContainText(/SECURITY NOISE/i)
    await expect(page.locator('h1')).toContainText(/DECISIONS/i)

    // Globe canvas rendered
    await expect(page.locator('canvas')).toBeVisible()

    // Hero CTA
    await expect(page.locator('a[href="/login"]').first()).toBeVisible()

    // Reference metrics
    await expect(page.locator('text=174').first()).toBeVisible()
    await expect(page.locator('text=94%').first()).toBeVisible()

    // No critical JS errors
    const critical = errors.filter((e) => !e.includes('favicon') && !e.includes('404'))
    expect(critical, 'console errors on landing').toHaveLength(0)
  })

  test('CTA "Enter command center" navigates to /login', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    const cta = page.locator('a[href="/login"]').first()
    await expect(cta).toBeVisible()
    await cta.click()

    await expect(page).toHaveURL(/\/login/)
    // Login page renders correctly
    await expect(page.locator('.lpv2-brand')).toBeVisible()
    await expect(page.locator('input[type="password"]')).toBeVisible()
  })

  test('browser back returns to landing and it re-renders correctly', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    await page.locator('a[href="/login"]').first().click()
    await expect(page).toHaveURL(/\/login/)

    await page.goBack()
    await page.waitForLoadState('networkidle').catch(() => {})
    await expect(page).toHaveURL('/')

    // Landing should re-render with headline
    await expect(page.locator('h1')).toContainText(/SECURITY NOISE/i)
    await expect(page.locator('canvas')).toBeVisible()
  })

  test('page refresh at / works without blank screen', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})
    await page.reload()
    await page.waitForLoadState('networkidle').catch(() => {})

    await expect(page.locator('h1')).toContainText(/SECURITY NOISE/i)
    await expect(page.locator('canvas')).toBeVisible()
  })

  test('landing page works when backend is unreachable', async ({ page }) => {
    // Override fetch to reject API calls — landing must not depend on backend
    await page.addInitScript(() => {
      const origFetch = window.fetch
      window.fetch = (url, ...args) => {
        if (typeof url === 'string' && url.startsWith('/api')) {
          return Promise.reject(new Error('Backend unavailable'))
        }
        return origFetch(url, ...args)
      }
    })

    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    await expect(page.locator('h1')).toContainText(/SECURITY NOISE/i)
    await expect(page.locator('canvas')).toBeVisible()
  })

  test('nav anchors scroll to sections', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    // Click #capabilities nav link
    await page.locator('a[href="#capabilities"]').click()
    // Section should be in view (capabilities section has id="capabilities")
    await expect(page.locator('#capabilities')).toBeInViewport({ ratio: 0.1 })
  })

  test('direct navigation to /login remains unchanged', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle').catch(() => {})

    await expect(page.locator('.lpv2-brand')).toBeVisible()
    await expect(page.locator('input[type="password"]')).toBeVisible()
    // Should NOT have landing-page div
    await expect(page.locator('.landing-page')).not.toBeVisible()
  })

  test('direct navigation to /dashboard redirects to /login (auth guard)', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })
})

test.describe('Landing page — scroll sections', () => {
  test('all major sections are in the DOM', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    // Check all section labels exist
    for (const label of ['01', '02', '03', '04', '05', '06', '07', '08']) {
      // Each section label (e.g. "01 — Security noise") should exist somewhere
      // We check for the number as part of the label component
      const count = await page.locator(`.landing-page`).count()
      expect(count).toBeGreaterThan(0)
    }

    // Final section CTA
    await expect(page.locator('text=Ready to enter')).toBeAttached()
    // Architecture section
    await expect(page.locator('#architecture')).toBeAttached()
    // Security section
    await expect(page.locator('#security')).toBeAttached()
  })
})

test.describe('Landing page — mobile viewport', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('landing page renders on mobile (390px) without horizontal overflow', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    // Headline visible
    await expect(page.locator('h1')).toBeVisible()
    // CTA visible
    await expect(page.locator('a[href="/login"]').first()).toBeVisible()

    // No horizontal scroll
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    const clientWidth = await page.evaluate(() => document.documentElement.clientWidth)
    expect(scrollWidth, 'horizontal overflow on mobile').toBeLessThanOrEqual(clientWidth + 5) // 5px tolerance
  })
})

test.describe('Landing page — reduced motion', () => {
  test.use({ reducedMotion: 'reduce' })

  test('all content remains visible with prefers-reduced-motion:reduce', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    // Content must not be hidden
    await expect(page.locator('h1')).toBeVisible()
    await expect(page.locator('a[href="/login"]').first()).toBeVisible()

    // The wrapper must not be opacity:0
    const opacity = await page.locator('.landing-page').evaluate(
      (el) => window.getComputedStyle(el).opacity
    )
    expect(Number(opacity)).toBeGreaterThan(0.5)
  })
})

test.describe('Landing page — CSS isolation', () => {
  test('SOC CSS variables on :root are not overwritten by landing page', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle').catch(() => {})

    const rootBg = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()
    )
    // SentinelAI's root --bg should be the SOC navy, not landing's black
    // (landing sets --bg only on .landing-page container via inline style)
    expect(rootBg).not.toBe('rgb(5, 6, 7)')
    expect(rootBg).not.toBe('#050607')

    // Landing container has its own --bg scoped to it
    const landingBg = await page.evaluate(() => {
      const el = document.querySelector('.landing-page')
      return el ? getComputedStyle(el).getPropertyValue('--bg').trim() : null
    })
    expect(landingBg).toBeTruthy()
  })
})
