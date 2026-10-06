# UI Design System & 3D Visuals

Phase 16 adds a token-driven design system, a shared component library, an app shell with a command
palette, a command-center dashboard, and lazy-loaded Three.js security visuals — with strict
performance and accessibility rules so the data UI is never blocked or broken by 3D.

## Design tokens

All visual constants live in `src/theme/tokens.css` as CSS variables and are the single source of
truth (no magic colors in components):

- **Surfaces / text / accent**: `--bg`, `--panel`, `--border`, `--text`, `--muted`, `--accent` (+
  elevated/strong variants).
- **Severity palette**: `--sev-low|medium|high|critical` with readable foregrounds, used everywhere.
- **Chart palette**: `--chart-1..6`, color-blind-aware.
- **Scale**: spacing (`--sp-*`), radius, typography (`--fs-*`), elevation (`--shadow-*`), motion
  (`--dur-*`, `--ease`).

**Theming**: dark SOC theme is the default; a light theme (`[data-theme="light"]`) is toggled from
the top bar and persisted (`ThemeProvider`, `localStorage`). The OS `prefers-color-scheme` is honored
until the user chooses. Severity colors target WCAG AA contrast against their surfaces, and **color is
never the only signal** — severity badges pair a dot, a glyph, and the text label.

## Component library (`src/components/ui`)

`Button, Card, Badge/SeverityBadge, StatTile (animated counter), Skeleton, EmptyState, ErrorState,
Tooltip, Tabs, Modal, Drawer, Toast (+useToast), Table`. The `Table` is sortable, has a sticky
header, and **virtualizes** rows past a threshold for large lists; live inserts flash via a
reduced-motion-safe highlight. Error messages are derived from the API error `code`
(`messageFromError`). Every data view exposes explicit loading / empty / error states.

## App shell

A fixed, collapsible, role-aware **sidebar** + **top bar** (command-palette search, site selector,
notifications, 3D-quality selector, theme toggle, user menu) rendered by `NavBar`. A **command
palette** (`Ctrl/Cmd+K`) jumps to pages, an incident by number, or quick actions — fully
keyboard-driven. Responsive down to tablet width (sidebar collapses to icons).

## Command-center dashboard

Threat-level indicator (ThreatCore orb + badge), KPI tiles (open incidents, events/min, alert
reduction, events/24h, critical+high), a live event stream (auto-refresh with a **pause toggle** and
a "last updated" stamp, new rows highlighted), severity/type charts (Recharts), a MITRE ATT&CK
heatmap, a pipeline-health card (Kafka lag / DLQ, "disabled" when Kafka is off), an AI-activity card,
and the 3D **AttackGlobe**.

## Three.js (react-three-fiber + drei)

Scenes: **AttackGlobe** (origins → protected site arcs, severity-colored, hover tooltips,
auto-rotate that pauses on interaction), **ThreatCore** (header risk orb; color + pulse follow the
threat level), **LoginBackdrop** (particle field). Data for the globe comes from
`GET /api/dashboard/geo-flows` (aggregated + cached).

### Performance & resilience rules (enforced)

- **Code-split**: three.js/r3f are **never in the main bundle** — each scene is `React.lazy`-imported
  behind a wrapper, so the 3D engine (~800 KB) loads only when a scene actually renders. (`geo.js`
  is deliberately `three`-free so the 2D fallback path can't drag three into main.)
- **Capped work**: device pixel ratio is capped (lower in Low quality), particle/arc counts are
  bounded (globe arcs ≤ 40), antialias off in Low quality.
- **Pause when invisible**: an `IntersectionObserver` + `visibilitychange` hook sets the r3f
  `frameloop` to `never` when the canvas is off-screen or the tab is hidden.
- **Quality setting**: High / Low / Off, stored in preferences. **Reduced motion forces Off.**
- **Graceful fallback**: when WebGL is unavailable, quality is Off, or a scene throws (each scene is
  wrapped in a `ThreeErrorBoundary`), a **2D/static equivalent** renders — the AttackGlobe falls back
  to a ranked origins **table**, which doubles as the screen-reader/text alternative. The data UI
  never waits on the 3D load.

## Accessibility

Keyboard navigation with a visible focus ring (`:focus-visible`), ARIA roles/labels on dialogs,
tabs, the command palette and tables, severity conveyed by icon+text (not color alone), and a
text/table alternative for every 3D view. Motion is disabled under `prefers-reduced-motion`.

## Testing

- **Vitest + Testing Library** (`npm test`): shared components, the table sort/empty logic, the
  command palette filtering, and the **3D fallback logic** (jsdom has no WebGL, so the wrappers must
  render their 2D/static equivalents — asserted).
- **Playwright** smoke (`e2e/`): login → dashboard → open incident → investigate → approve action,
  plus the reduced-motion and WebGL-off paths. Run with `npx playwright test` (needs browsers
  installed in the CI/dev environment).

## Bundle (production build)

The 3D engine is isolated from the main chunk — see the README UI section for the latest sizes.
