#!/usr/bin/env bash
#
# One-command local security scan for SentinelAI. Runs the scanners that don't need a standing
# server and writes all reports under reports/ (gitignored). Each step is best-effort: a missing
# tool is reported and skipped, never fatal, so you can run whatever is installed.
#
# Covered here: gitleaks (secrets), OWASP Dependency-Check (Java CVEs), npm audit (JS CVEs),
# Trivy filesystem/IaC + image scans, and the backend security test suite.
# Server-based scans (SonarQube, ZAP) have their own compose files — see docs/security/README.
#
# Usage:  ./scripts/security-scan.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORTS="$ROOT/reports"
mkdir -p "$REPORTS"
cd "$ROOT"

: "${JAVA_HOME:=}"   # callers on multi-JDK machines should export JAVA_HOME=<JDK 21>
FAIL=0

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$1"; }
have() { command -v "$1" >/dev/null 2>&1; }

step "gitleaks — secret scanning"
if have gitleaks; then
  gitleaks detect --config .gitleaks.toml --report-path "$REPORTS/gitleaks.json" --redact \
    || { echo "gitleaks found potential secrets (see $REPORTS/gitleaks.json)"; FAIL=1; }
else
  echo "SKIP: gitleaks not installed (brew install gitleaks)"
fi

step "OWASP Dependency-Check — backend dependency CVEs (CVSS>=7 fails)"
if have mvn; then
  ( cd backend && mvn -q -Psecurity verify -DskipTests \
      -Dformats=HTML,JSON -DoutputDirectory="$REPORTS/dependency-check" ) \
    || { echo "Dependency-Check failed or found HIGH/CRITICAL CVEs"; FAIL=1; }
else
  echo "SKIP: mvn not installed"
fi

step "npm audit — frontend dependency CVEs (high/critical)"
if have npm; then
  ( cd frontend && npm audit --audit-level=high --json > "$REPORTS/npm-audit.json" ) \
    || { echo "npm audit found high/critical advisories (see $REPORTS/npm-audit.json)"; FAIL=1; }
else
  echo "SKIP: npm not installed"
fi

step "Trivy — filesystem / IaC scan (HIGH,CRITICAL fail)"
if have trivy; then
  trivy fs --scanners vuln,secret,misconfig --severity HIGH,CRITICAL \
    --format json --output "$REPORTS/trivy-fs.json" . \
    || { echo "Trivy fs found HIGH/CRITICAL issues (see $REPORTS/trivy-fs.json)"; FAIL=1; }
else
  echo "SKIP: trivy not installed (brew install trivy)"
fi

step "Trivy — built image scan (optional; images must be built first)"
if have trivy && have docker; then
  for img in sentinel-backend sentinel-frontend sentinel-ml; do
    if docker image inspect "$img" >/dev/null 2>&1; then
      trivy image --severity HIGH,CRITICAL --format json \
        --output "$REPORTS/trivy-image-$img.json" "$img" \
        || { echo "Trivy image $img found HIGH/CRITICAL issues"; FAIL=1; }
    else
      echo "SKIP: image $img not built (docker build -t $img ...)"
    fi
  done
else
  echo "SKIP: trivy or docker not available"
fi

step "Backend security regression tests"
if have mvn; then
  ( cd backend && mvn -q -o test -Dtest='com.sentinelai.security.*,JwtSecurityTest' ) \
    || { echo "Security tests failed"; FAIL=1; }
fi

step "Done"
echo "Reports written to $REPORTS/"
[ "$FAIL" -eq 0 ] && echo "No blocking findings." || echo "One or more scans reported findings — review reports/."
exit "$FAIL"
