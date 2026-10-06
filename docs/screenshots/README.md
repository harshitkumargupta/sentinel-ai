# Screenshots

Generated for the report/demo by Playwright:

```bash
cd frontend
npx playwright install chromium        # one-time
BASE_URL=http://localhost:5173 npx playwright test e2e/screenshots.spec.js
```

Produces: `01-login.png`, `02-dashboard.png`, `03-dashboard-light.png`, `04-command-palette.png`.
Requires the app running (dev server or the prod stack) and a seeded admin user.
