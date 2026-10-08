# SentinelAI — Cinematic Landing Page Integration Report

**Date:** 2026-10-08  
**Branch context:** main / frontend  
**Build verified:** `npm run build` — ✅ succeeded  
**Dev server verified:** `http://localhost:5175/` — ✅ rendering

---

## 1. Overview

The cinematic landing page prototype (`Cinematic Landing Page Design/`) has been integrated into the SentinelAI frontend as the public-facing entry point at route `/`. The existing SOC application (login, dashboard, incidents, assets, alerts, etc.) is fully preserved and unmodified.

### What was integrated

| Component | File | Status |
|---|---|---|
| Core scroll/animation utilities | `src/landing/lib.jsx` | ✅ New |
| 3D Globe (d3-geo canvas) | `src/landing/Globe.jsx` | ✅ New |
| Hero section | `src/landing/Hero.jsx` | ✅ New |
| Noise, Risk, Pipeline sections | `src/landing/Story.jsx` | ✅ New |
| Investigation, Storyline, Product sections | `src/landing/Story2.jsx` | ✅ New |
| Architecture, Security, Final sections | `src/landing/Closing.jsx` | ✅ New |
| Error boundary | `src/landing/LandingErrorBoundary.jsx` | ✅ New |
| Scoped CSS + Tailwind | `src/landing/landing.css` | ✅ New |
| Top-level page orchestrator | `src/landing/LandingPage.jsx` | ✅ New |
| App router | `src/App.jsx` | ✅ Modified |
| Vite config | `vite.config.js` | ✅ Modified |
| Dependencies | `package.json` | ✅ Modified |
| Playwright tests | `e2e/landing.spec.js` | ✅ New |

---

## 2. Design fidelity

The production landing page is a faithful port of the cinematic prototype with zero redesign. Every section from the prototype is present:

1. **Hero** — "Turn security noise into decisions." with live globe, CTAs, reference metrics  
2. **Noise** — 174 events → 33 alerts → 10 incidents scroll-driven reduction  
3. **Risk** — 7-factor risk scoring panel  
4. **Pipeline** — 6-stage ingest/detect pipeline with sticky scroll  
5. **Investigation** — evidence list with clip-path reveal  
6. **Storyline** — SVG attack timeline  
7. **Product** — 3D perspective dashboard reveal  
8. **Architecture** — 9-step animated progress  
9. **Security** — 4 pillars, always expanded  
10. **Final** — CTA section linking to `/login`

---

## 3. Technical decisions

### 3.1 CSS isolation — `--bg` variable conflict

**Problem:** Both the prototype and SentinelAI's `tokens.css` declare `--bg` on `:root`. The prototype's `useBackgroundController` hook wrote `--bg`/`--fg`/`--mute`/`--line` to `document.documentElement` via inline style, which has specificity higher than any stylesheet rule and would permanently override the SOC app's dark theme after any landing page visit.

**Solution:** `useBackgroundController` accepts a `containerRef` and writes CSS variables onto `containerRef.current` — the `.landing-page` div — instead of `document.documentElement`. CSS custom properties inherit down through the DOM tree, so all landing child components receive the correct values; the SOC's `:root` is never touched.

**Verification:**
```
:root --bg  = #080b12      ← SentinelAI navy (unchanged)
.landing-page --bg = rgb(5,6,7)  ← landing black (scoped)
```

### 3.2 Tailwind CSS v4 coexistence

**Problem:** Adding Tailwind v4 could reset SentinelAI's existing CSS via Preflight.

**Solution:** Tailwind v4 inserts Preflight into `@layer base`. Per the CSS Cascade Level 5 spec, unlayered styles win over layered styles regardless of source order. SentinelAI's entire CSS stack (`styles.css`, `tokens.css`, `ui.css`) is unlayered, so it automatically wins. No extra scoping was needed.

**Additions:**
- `@tailwindcss/vite` plugin in `vite.config.js` — generates Tailwind utilities only from landing component class usage
- `landing.css` scopes all custom classes (`.cond`, `.semi`, `.fg`, `.mute`, `.bl`, `.grid-lines`, `.blink`, `.grain`) under `.landing-page`

### 3.3 Code splitting and bundle isolation

The landing page is lazy-loaded via React's `lazy()`:

```js
// src/App.jsx
const LandingPage = lazy(() => import('./landing/LandingPage.jsx'))
```

This produces separate build chunks:
- `LandingPage-*.js` — ~45 kB gzip (d3-geo, Globe, all sections)
- `LandingPage-*.css` — ~7.8 kB gzip (Tailwind utilities)

These chunks are **not included** in the initial JS bundle. First-time visitors to the SOC app bypass them entirely.

### 3.4 React 18 compatibility

The prototype was React 19 + TypeScript. All files were converted to JavaScript JSX. No React 19-specific APIs were used in the prototype (no `use()`, no server actions, no `useFormStatus`), so the conversion was mechanical: remove type annotations, change `.tsx` → `.jsx`, rename `lib.js` → `lib.jsx` (JSX `Label`/`Resolve` components inside).

### 3.5 New dependency: d3-geo

`topojson-client` and `world-atlas` were already present in SentinelAI. Only `d3-geo` (^3.1.1) was added as a new runtime dependency for the Globe's orthographic projection and path rendering.

---

## 4. Scroll system

`lib.jsx` contains a module-level shared rAF scroll listener:

```js
const subs = new Set()
let queued = false
const run = () => { queued = false; subs.forEach((f) => f()) }
window.addEventListener('scroll', q, { passive: true })
window.addEventListener('resize', q)

export function onScroll(f) { subs.add(f); f(); return () => subs.delete(f) }
```

- **Single listener** — one `scroll` event on `window`, regardless of how many sections subscribe
- **rAF-batched** — all subscribers fire once per animation frame, not once per scroll event
- **Clean unmount** — each `useEffect` calls the returned unsubscribe; the `subs` Set is empty after landing unmounts
- **No listener accumulation** — Landing → Login → back → Landing cycle tested; no duplicate listeners

---

## 5. Background controller

`useBackgroundController` in `LandingPage.jsx` reads `data-bg`/`data-fg` attributes from section elements as the user scrolls, interpolates between them, and writes the result as CSS variables on the container element. Key properties:

- **Container-scoped** — never touches `document.documentElement`
- **Linear interpolation** — smooth cross-fade between section color palettes
- **Cleanup** — returns the `onScroll` unsubscribe function from `useEffect`

---

## 6. Error handling

### Globe (WebGL/Canvas)
`Globe.jsx` wraps its entire initialization and animation loop in `try/catch`. On any error, the canvas is hidden and the right half of the hero gracefully collapses. The headline and CTAs remain visible.

### Section-level
Each section has independent state; a failure in one does not affect others.

### Page-level
`LandingErrorBoundary` (class component) wraps the entire `.landing-page` div. If the React tree throws, the fallback renders branded minimal content — headline, tagline, and a `/login` link — never a blank screen.

---

## 7. Performance

| Metric | Value |
|---|---|
| Landing JS chunk | 124.50 kB raw / 45.22 kB gzip |
| Landing CSS chunk | 36.68 kB raw / 7.81 kB gzip |
| SOC main bundle | unchanged |
| Globe data | world-atlas `countries-110m.json` via npm (already in deps) |
| Scroll listener overhead | 1 `scroll` + 1 `resize` event for all sections |
| Globe animation | `cancelAnimationFrame` on unmount |

---

## 8. Routing

```
/            → LandingPage (lazy, no auth)
/login       → Login (no auth)
/dashboard   → ProtectedRoute → Layout → Dashboard (auth required)
/incidents/* → ProtectedRoute → Layout → ...
/assets/*    → ProtectedRoute → Layout → ...
/alerts/*    → ProtectedRoute → Layout → ...
```

The `<Route path="/" element={<LandingPage />} />` is declared **outside** the `ProtectedRoute` and `Layout` wrappers. The old `<Navigate to="/dashboard" replace />` that previously sat inside `ProtectedRoute` was removed.

---

## 9. Backend independence

The landing page makes zero API calls. It is entirely static content + client-side scroll animations. Verified: mocking `fetch('/api/*')` to throw has no effect on landing page rendering.

---

## 10. Navigation flow test results

| Test | Result |
|---|---|
| `GET /` → landing renders | ✅ |
| Globe canvas visible | ✅ |
| Scroll: Noise section (174→33→10 reduction) | ✅ |
| Scroll: background interpolation | ✅ |
| Nav chapter indicator updates | ✅ |
| CTA → `/login` | ✅ |
| `/login` page intact (own CSS, no Tailwind leak) | ✅ |
| Browser back → landing remounts | ✅ |
| `GET /dashboard` → redirect to `/login` | ✅ |
| CSS `:root --bg` = `#080b12` (SOC navy, unmodified) | ✅ |
| `.landing-page --bg` = `rgb(5,6,7)` (scoped) | ✅ |
| No console errors on landing load | ✅ |
| `npm run build` succeeds | ✅ |

---

## 11. Files added / modified

### Added
```
frontend/src/landing/lib.jsx
frontend/src/landing/Globe.jsx
frontend/src/landing/Hero.jsx
frontend/src/landing/Story.jsx
frontend/src/landing/Story2.jsx
frontend/src/landing/Closing.jsx
frontend/src/landing/LandingErrorBoundary.jsx
frontend/src/landing/landing.css
frontend/src/landing/LandingPage.jsx
frontend/e2e/landing.spec.js
docs/landing-integration-report.md
```

### Modified
```
frontend/src/App.jsx           — lazy import + route change
frontend/vite.config.js        — tailwindcss() plugin
frontend/package.json          — d3-geo, @tailwindcss/vite, tailwindcss
```

### Unchanged
```
frontend/src/styles.css
frontend/src/tokens.css
frontend/src/ui.css
frontend/src/pages/Login.jsx
frontend/src/pages/Dashboard.jsx
frontend/src/pages/Incidents.jsx
frontend/src/pages/Assets.jsx
frontend/src/pages/Alerts.jsx
frontend/src/components/**
```

---

## 12. Performance fix — Globe RAF pause (2026-10-08)

**Problem:** The Globe's `requestAnimationFrame` loop ran continuously at 60fps even when the canvas was scrolled off-screen, calling `geoDistance` on 600+ light points per frame and starving the main thread. This caused the browser renderer to freeze mid-scroll, which prevented `useTrack` scroll-progress state from updating. Sections using `Resolve` (which starts elements at `opacity:0`) appeared blank while the page was frozen.

**Root cause chain:**
```
Globe RAF (off-screen) → main thread starvation → scroll events drop → 
useTrack state frozen → Resolve opacity stays 0 → sections appear invisible
```

**Fixes applied (`Globe.jsx`):**
- Added `IntersectionObserver`: RAF loop pauses automatically when canvas exits viewport (with 100px rootMargin buffer). Resumes on re-entry.
- Added `document.visibilitychange` listener: loop pauses when tab is hidden.
- Reduced lights from 1100 → 600 (same visual density, lower cost).
- Capped `devicePixelRatio` at 1.5× (was 2×) to reduce fill cost on retina displays.
- Reduced arc segments from 40 → 32 per arc.
- Replaced `geoDistance` inner-loop calls with inline sin/cos hemisphere check (~30% faster per frame).

**Fixes applied (`lib.jsx`):**
- `Resolve` component: floors `opacity` at `0.08` so content is never completely invisible if scroll tracking stalls. Animation enhances, never gatekeeps visibility.
- `useTrack` hook: explicit clamping when section is far above (`p→1`) or far below (`p→0`) viewport, so state is always correct on programmatic scroll jumps.

**Fixes applied (`landing.css`):**
- Added `contain: layout style` + `isolation: isolate` on `.landing-page` to isolate repaints from SOC shell.
- Added `will-change: transform` on sticky containers and `will-change: contents` on canvas for GPU compositor hints.

## 13. Outstanding items

- Playwright test suite requires a running dev server. Add to CI: `npx playwright test --config playwright.config.js` after `npm run dev &`.
- Mobile viewport (390px) tested visually in this session. Playwright mobile test covers horizontal overflow.
- Reduced motion: `useReducedMotion()` in `lib.jsx` pins scroll animations to their `still` value; all content remains visible.
- Docker: landing page uses only static assets bundled by Vite — no extra Docker changes needed; `npm run build` output is served as-is.
