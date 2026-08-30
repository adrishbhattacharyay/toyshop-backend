/**
 * User Service — Smoke Test
 *
 * Minimal single-VU run to verify the service is up and all endpoints
 * respond correctly before running the full load test.
 *
 * Run:
 *   k6 run user-service-perf-test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, registerUser, authHeaders } from './lib/auth.js';
import { htmlReport } from './lib/report.js';

const errorRate = new Rate('errors');

export const options = {
  vus: 1,
  iterations: 5,
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed:   ['rate<0.01'],
    errors:            ['rate<0.01'],
  },
};

export function setup() {
  const user = registerUser('smoke');
  if (!user) throw new Error('[smoke] Could not register test user — is the service running?');
  console.log(`[smoke] Test user: ${user.username} @ ${BASE_URL}`);
  return { user };
}

export default function (data) {
  const { user } = data;

  // Login
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ usernameOrEmail: user.username, password: user.password }),
    { headers: JSON_HEADERS }
  );
  const loginOk = check(loginRes, {
    'smoke login: 200':            (r) => r.status === 200,
    'smoke login: accessToken':    (r) => r.json('accessToken') !== undefined,
  });
  errorRate.add(!loginOk);
  if (!loginOk) { sleep(1); return; }

  const token = loginRes.json('accessToken');
  const hdrs = authHeaders(token);

  // Profile
  const profileRes = http.get(`${BASE_URL}/api/v1/users/me`, { headers: hdrs });
  errorRate.add(!check(profileRes, { 'smoke profile: 200': (r) => r.status === 200 }));

  // Health check (direct to gateway actuator)
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  errorRate.add(!check(healthRes, { 'smoke health: 200': (r) => r.status === 200 }));

  // Refresh
  const refreshRes = http.post(
    `${BASE_URL}/api/v1/auth/refresh`,
    JSON.stringify({ refreshToken: loginRes.json('refreshToken') }),
    { headers: JSON_HEADERS }
  );
  errorRate.add(!check(refreshRes, { 'smoke refresh: 200': (r) => r.status === 200 }));

  sleep(0.5);
}

export function handleSummary(data) {
  return htmlReport(data, 'user-service-smoke');
}
