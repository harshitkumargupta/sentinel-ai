import http from 'k6/http';
import { check } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USER = __ENV.ADMIN_USER || 'admin';
const PASS = __ENV.ADMIN_PASS || 'Admin@123';

/** Log in once (in setup) and return a bearer token for the VUs to reuse. */
export function login() {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username: USER, password: PASS }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login 200': (r) => r.status === 200 });
  return res.json('data.accessToken');
}

export function authHeaders(token) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };
}

export function randomEvent(i) {
  const ips = ['203.0.113.7', '198.51.100.3', '192.0.2.44'];
  return JSON.stringify({
    eventType: 'FAILED_LOGIN',
    severity: 'LOW',
    sourceIp: ips[i % ips.length],
    username: `load-user-${i % 50}`,
  });
}
