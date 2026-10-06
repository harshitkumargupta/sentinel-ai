# Accepted Risks

Every deviation from a clean scan or a hardening default is recorded here with an owner, a
justification, and a review date. An OWASP Dependency-Check `<suppress>` or a ZAP/Sonar waiver is
**not valid** without a matching row here.

| ID | Area | Decision | Justification | Added | Revisit |
|----|------|----------|---------------|-------|---------|
| AR-01 | Build pipeline | OWASP Dependency-Check runs under `mvn -Psecurity verify`, not the default `mvn verify`. | The NVD data feed is slow and rate-limited without an API key, which makes an unconditional bind flaky and would break routine builds/CI. The scan is still mandatory before release and is wired into `scripts/security-scan.sh` and the CI security job. Set `NVD_API_KEY` to run it fast. | 2026-10-06 | When an `NVD_API_KEY` is provisioned in CI, bind it to `verify`. |
| AR-02 | Frontend CSP | `style-src` allows `'unsafe-inline'`. | Vite injects a small amount of inline CSS for the bundled styles; scripts remain `'self'` only (no `script-src 'unsafe-inline'`), so the XSS-relevant vector is closed. | 2026-10-06 | Move to hashed/nonce styles if a styling pipeline change makes it practical. |
| AR-03 | CSRF | CSRF protection is disabled in Spring Security. | The API is stateless and authenticated solely by a `Authorization: Bearer` JWT (no cookies, no ambient session), so there is no CSRF vector. Documented in `checklist.md` (A01) and asserted by the stateless/`SessionCreationPolicy.STATELESS` config. | 2026-10-06 | Revisit if any cookie-based auth is introduced. |
| AR-04 | Frontend deps | `npm audit` reports 1 high + 3 moderate, all in **vite** (`<=6.4.2`) and its toolchain. | Vite is a **devDependency**: the advisories (dev-server path traversal, `server.fs.deny` bypass, launch-editor NTLM) affect the local dev server — mostly on Windows — not the static bundle that nginx serves in production. The only fix is a breaking major (vite 8), which is deferred to avoid destabilizing the verified build. The shipped artifact is unaffected. | 2026-10-06 | Upgrade to vite 8 on the next frontend toolchain pass and re-run `npm audit`. |

## Dependency-Check suppressions

None in effect. Any future entry in `backend/owasp-suppressions.xml` must add a row above and a
`<notes>` justification in the suppression file itself.
