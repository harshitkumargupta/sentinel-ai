// Mixed soak: steady blend of ingest + reads for 15 minutes to surface leaks/GC/lag drift.
// Run: k6 run load-tests/soak.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE, login, authHeaders, randomEvent } from './lib.js';

export const options = {
  scenarios: {
    writes: { executor: 'constant-vus', vus: 20, duration: '15m', exec: 'write' },
    reads: { executor: 'constant-vus', vus: 30, duration: '15m', exec: 'read' },
  },
  thresholds: { http_req_failed: ['rate<0.02'], http_req_duration: ['p(95)<600'] },
};

export function setup() { return { token: login() }; }
export function write(data) {
  const res = http.post(`${BASE}/api/events`, randomEvent(__ITER), authHeaders(data.token));
  check(res, { 'write 2xx': (r) => r.status >= 200 && r.status < 300 });
}
export function read(data) {
  const res = http.get(`${BASE}/api/dashboard/summary`, authHeaders(data.token));
  check(res, { 'read 200': (r) => r.status === 200 });
}
