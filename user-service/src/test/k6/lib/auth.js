/**
 * Shared authentication helpers for k6 performance tests.
 * All tests target the API Gateway at :8080 — the real production path.
 */
import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

/**
 * Registers a unique test user and returns { username, password, email }.
 * Returns null if registration fails (e.g. user already exists).
 */
export function registerUser(suffix) {
  const username = `perf_${suffix}_${Date.now()}`;
  const email = `${username}@perf.test`;
  const password = 'Perf@1234';

  const res = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username, email, password, firstName: 'Perf', lastName: 'Test' }),
    { headers: JSON_HEADERS }
  );

  if (res.status !== 201) {
    console.warn(`[auth] Registration failed for ${username}: ${res.status} ${res.body}`);
    return null;
  }
  return { username, password, email };
}

/**
 * Logs in with the given credentials and returns the accessToken string.
 * Returns null on failure.
 */
export function login(usernameOrEmail, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ usernameOrEmail, password }),
    { headers: JSON_HEADERS }
  );

  const ok = check(res, {
    'login 200': (r) => r.status === 200,
    'login has accessToken': (r) => r.json('accessToken') !== undefined,
  });

  if (!ok) {
    console.warn(`[auth] Login failed for ${usernameOrEmail}: ${res.status}`);
    return null;
  }
  return res.json('accessToken');
}

/**
 * Returns Authorization headers for an authenticated request.
 */
export function authHeaders(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/**
 * Logs in as the seeded admin user.
 * Credentials come from user-service/src/main/resources/data.sql
 */
export function adminLogin() {
  return login('admin', 'Test@123');
}
