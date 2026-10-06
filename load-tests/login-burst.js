// Login burst: hammer the login endpoint to exercise lockout / rate limiting. Expect a mix of 401
// (bad creds) and 429 (rate limited) when rate limiting is enabled. Run: k6 run load-tests/login-burst.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE } from './lib.js';

export const options = {
  scenarios: { burst: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: '30s',
    preAllocatedVUs: 50, maxVUs: 100 } },
  thresholds: { http_req_failed: ['rate<1'] }, // all non-2xx expected; this is an abuse test
};

export default function () {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username: 'admin', password: 'wrong-password' }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'rejected (401/423/429)': (r) => [401, 423, 429].includes(r.status) });
}
