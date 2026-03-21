import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration');
const registerDuration = new Trend('register_duration');
const getUserDuration = new Trend('get_user_duration');

// Test configuration
export const options = {
  stages: [
    { duration: '10s', target: 10 },  // Ramp up
    { duration: '30s', target: 50 },  // Stay at 50 users
    { duration: '10s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.01'],
    login_duration: ['p(95)<1000'],
    register_duration: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export function setup() {
  // Register a test user for login tests
  const registerPayload = JSON.stringify({
    username: `perfuser_${Date.now()}`,
    email: `perf_${Date.now()}@example.com`,
    password: 'Password123!',
    firstName: 'Performance',
    lastName: 'Test',
  });

  const registerRes = http.post(`${BASE_URL}/api/v1/auth/register`, registerPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  if (registerRes.status !== 201) {
    console.log('Registration failed, user might already exist');
  }

  return { registered: true };
}

export default function (data) {
  // Test 1: User Registration
  const registerStart = new Date();
  const registerPayload = JSON.stringify({
    username: `perfuser_${Math.random().toString(36).substring(7)}`,
    email: `perf_${Math.random().toString(36).substring(7)}@example.com`,
    password: 'Password123!',
    firstName: 'Performance',
    lastName: 'Test',
  });

  const registerRes = http.post(`${BASE_URL}/api/v1/auth/register`, registerPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const registerTime = new Date() - registerStart;
  registerDuration.add(registerTime);

  const registerOk = check(registerRes, {
    'registration successful': (r) => r.status === 201,
    'registration returns user data': (r) => r.json('id') !== undefined,
  });

  errorRate.add(!registerOk);

  // Test 2: User Login
  const loginPayload = JSON.stringify({
    usernameOrEmail: 'testuser',
    password: 'Password123!',
  });

  const loginStart = new Date();
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const loginTime = new Date() - loginStart;
  loginDuration.add(loginTime);

  let token = null;
  const loginOk = check(loginRes, {
    'login successful': (r) => r.status === 200,
    'login returns token': (r) => {
      token = r.json('accessToken');
      return token !== undefined;
    },
  });

  errorRate.add(!loginOk);

  // Test 3: Get User Profile (with auth)
  if (token) {
    const getUserStart = new Date();
    const getUserRes = http.get(`${BASE_URL}/api/v1/users/me`, {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    });

    const getUserTime = new Date() - getUserStart;
    getUserDuration.add(getUserTime);

    const getUserOk = check(getUserRes, {
      'get user successful': (r) => r.status === 200,
      'get user returns username': (r) => r.json('username') !== undefined,
    });

    errorRate.add(!getUserOk);
  }

  // Test 4: Health Check
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  const healthOk = check(healthRes, {
    'health check successful': (r) => r.status === 200,
  });
  errorRate.add(!healthOk);

  sleep(1);
}

export function teardown(data) {
  console.log('Performance test completed');
  console.log(`Error rate: ${errorRate.values.rate}`);
}