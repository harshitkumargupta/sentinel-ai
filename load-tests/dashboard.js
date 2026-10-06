// Dashboard read mix: exercises the cached read endpoints (compare cached vs uncached by toggling
// Redis). Run: k6 run load-tests/dashboard.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE, login, authHeaders } from './lib.js';

export const options = {
  scenarios: { reads: { executor: 'constant-vus', vus: 50, duration: '1m' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<300'] },
};

export function setup() { return { token: login() }; }
export default function (data) {
  const h = authHeaders(data.token);
  const paths = ['/api/dashboard/summary', '/api/dashboard/alert-reduction',
    '/api/dashboard/mitre-coverage', '/api/dashboard/geo-flows', '/api/incidents?size=20'];
  for (const p of paths) {
    const res = http.get(`${BASE}${p}`, h);
    check(res, { 'read 200': (r) => r.status === 200 });
  }
}
