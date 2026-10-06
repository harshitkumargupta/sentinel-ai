// Ingest ramp: push events as fast as the machine allows. Compare sync vs Kafka by toggling
// KAFKA_ENABLED on the backend. Run: k6 run load-tests/ingest.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE, login, authHeaders, randomEvent } from './lib.js';

export const options = {
  scenarios: {
    ingest: { executor: 'ramping-vus', startVUs: 0,
      stages: [ { duration: '30s', target: 50 }, { duration: '1m', target: 200 }, { duration: '30s', target: 0 } ] },
  },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] },
};

export function setup() { return { token: login() }; }
export default function (data) {
  const res = http.post(`${BASE}/api/events`, randomEvent(__ITER), authHeaders(data.token));
  check(res, { 'ingest 2xx': (r) => r.status >= 200 && r.status < 300 });
}
