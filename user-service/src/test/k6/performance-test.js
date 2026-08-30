/**
 * User Service — Load Performance Test
 *
 * Covers: register, login, get profile, refresh token
 * Target: API Gateway :8080  (real production path through Eureka + LB)
 *
 * Run:
 *   k6 run performance-test.js
 *   k6 run --env BASE_URL=http://staging:8080 performance-test.js
 *
 * Reports written to ./reports/
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, registerUser, login, authHeaders } from './lib/auth.js';
import { htmlReport } from './lib/report.js';

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration', true);
const registerDuration = new Trend('register_duration', true);
const profileDuration = new Trend('profile_duration', true);
const refreshDuration = new Trend('refresh_duration', true);
const successfulLogins = new Counter('successful_logins');
const failedLogins = new Counter('failed_logins');

// ---------------------------------------------------------------------------
// Test configuration — three stages: ramp-up → sustained load → ramp-down
// ---------------------------------------------------------------------------
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // warm-up
    { duration: '1m',  target: 50 },  // sustained load
    { duration: '30s', target: 100 }, // stress spike
    { duration: '30s', target: 0 },   // cool-down
  ],
  thresholds: {
    // Overall HTTP SLOs
    http_req_duration:    ['p(95)<800', 'p(99)<1500'],
    http_req_failed:      ['rate<0.05'],
    errors:               ['rate<0.05'],
    // Per-operation SLOs
    login_duration:       ['p(95)<500'],
    register_duration:    ['p(95)<700'],
    profile_duration:     ['p(95)<300'],
    refresh_duration:     ['p(95)<400'],
  },
};

// ---------------------------------------------------------------------------
// Setup — pre-create 20 users so login tests have valid credentials
// ---------------------------------------------------------------------------
export function setup() {
  const users = [];
  for (let i = 0; i < 20; i++) {
    const u = registerUser(`usr${i}`);
    if (u) users.push(u);
  }
  console.log(`[setup] Created ${users.length} test users against ${BASE_URL}`);
  return { users };
}

// ---------------------------------------------------------------------------
// Main VU loop
// ---------------------------------------------------------------------------
export default function (data) {
  const { users } = data;
  if (!users || users.length === 0) {
    console.error('[VU] No test users available — aborting VU');
    return;
  }

  const user = users[Math.floor(Math.random() * users.length)];

  // --- 1. Login ---
  const loginStart = Date.now();
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ usernameOrEmail: user.username, password: user.password }),
    { headers: JSON_HEADERS, tags: { name: 'auth_login' } }
  );
  loginDuration.add(Date.now() - loginStart);

  const loginOk = check(loginRes, {
    'login: status 200':       (r) => r.status === 200,
    'login: has accessToken':  (r) => r.json('accessToken') !== undefined,
    'login: has refreshToken': (r) => r.json('refreshToken') !== undefined,
    'login: <500ms':           (r) => r.timings.duration < 500,
  });

  errorRate.add(!loginOk);
  if (loginOk) {
    successfulLogins.add(1);
  } else {
    failedLogins.add(1);
    sleep(1);
    return;
  }

  const token = loginRes.json('accessToken');
  const refreshToken = loginRes.json('refreshToken');
  const hdrs = authHeaders(token);

  // --- 2. Get own profile ---
  const profileStart = Date.now();
  const profileRes = http.get(
    `${BASE_URL}/api/v1/users/me`,
    { headers: hdrs, tags: { name: 'user_profile' } }
  );
  profileDuration.add(Date.now() - profileStart);

  const profileOk = check(profileRes, {
    'profile: status 200':    (r) => r.status === 200,
    'profile: has username':  (r) => r.json('username') !== undefined,
    'profile: <300ms':        (r) => r.timings.duration < 300,
  });
  errorRate.add(!profileOk);

  // --- 3. Refresh token ---
  const refreshStart = Date.now();
  const refreshRes = http.post(
    `${BASE_URL}/api/v1/auth/refresh`,
    JSON.stringify({ refreshToken }),
    { headers: JSON_HEADERS, tags: { name: 'auth_refresh' } }
  );
  refreshDuration.add(Date.now() - refreshStart);

  const refreshOk = check(refreshRes, {
    'refresh: status 200':      (r) => r.status === 200,
    'refresh: new accessToken': (r) => r.json('accessToken') !== undefined,
  });
  errorRate.add(!refreshOk);

  // --- 4. Register a new user (write load) ---
  const regStart = Date.now();
  const regRes = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({
      username: `vu_${__VU}_${__ITER}_${Date.now()}`,
      email:    `vu_${__VU}_${__ITER}_${Date.now()}@perf.test`,
      password: 'Perf@1234',
      firstName: 'VU',
      lastName:  'Test',
    }),
    { headers: JSON_HEADERS, tags: { name: 'auth_register' } }
  );
  registerDuration.add(Date.now() - regStart);

  const regOk = check(regRes, {
    'register: status 201': (r) => r.status === 201,
    'register: has id':     (r) => r.json('id') !== undefined,
    'register: <700ms':     (r) => r.timings.duration < 700,
  });
  errorRate.add(!regOk);

  sleep(Math.random() * 1.5 + 0.5); // 0.5–2s think time
}

// ---------------------------------------------------------------------------
// Report output
// ---------------------------------------------------------------------------
export function handleSummary(data) {
  return htmlReport(data, 'user-service-load');
}
