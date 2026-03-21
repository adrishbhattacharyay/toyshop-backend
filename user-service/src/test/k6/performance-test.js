import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const loginTrend = new Trend('login_duration');
const registrationTrend = new Trend('registration_duration');
const userRetrievalTrend = new Trend('user_retrieval_duration');

// Counters
const successfulLogins = new Counter('successful_logins');
const failedLogins = new Counter('failed_logins');
const successfulRegistrations = new Counter('successful_registrations');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // Warm up
    { duration: '1m', target: 50 },   // Load testing
    { duration: '30s', target: 100 }, // Stress testing
    { duration: '30s', target: 0 },   // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
    http_req_failed: ['rate<0.1'],    // Error rate should be below 10%
    errors: ['rate<0.1'],
    login_duration: ['p(95)<300'],
    registration_duration: ['p(95)<400'],
  },
};

const BASE_URL = 'http://localhost:8081';
const USERS = [];

// Setup function - runs before the test starts
export function setup() {
  console.log('Performance test setup starting...');
  
  // Pre-register some test users
  for (let i = 0; i < 20; i++) {
    const username = `perfuser${i}`;
    const password = 'Password123!';
    const email = `perfuser${i}@example.com`;

    const registerPayload = JSON.stringify({
      username: username,
      email: email,
      password: password,
      firstName: 'Performance',
      lastName: 'Test'
    });

    const registerResponse = http.post(`${BASE_URL}/api/v1/auth/register`, registerPayload, {
      headers: { 'Content-Type': 'application/json' },
    });

    if (registerResponse.status === 201) {
      USERS.push({ username, password, email });
    }
  }

  console.log(`Setup complete. Created ${USERS.length} test users.`);
  return { users: USERS };
}

// Main test function
export default function (data) {
  const userIndex = Math.floor(Math.random() * data.users.length);
  const testUser = data.users[userIndex];

  // Test 1: User Registration
  const registerPayload = JSON.stringify({
    username: `newuser${Math.random().toString(36).substring(7)}`,
    email: `new${Math.random().toString(36).substring(7)}@example.com`,
    password: 'Password123!',
    firstName: 'Performance',
    lastName: 'Test'
  });

  const regStart = new Date().getTime();
  const registerResponse = http.post(`${BASE_URL}/api/v1/auth/register`, registerPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  const regEnd = new Date().getTime();

  registrationTrend.add(regEnd - regStart);

  const registerCheck = check(registerResponse, {
    'register status is 201': (r) => r.status === 201,
    'register response time < 500ms': (r) => r.timings.duration < 500,
  });

  errorRate.add(!registerCheck);
  if (registerCheck) {
    successfulRegistrations.add(1);
  }

  // Test 2: User Login
  const loginPayload = JSON.stringify({
    usernameOrEmail: testUser.username,
    password: testUser.password
  });

  const loginStart = new Date().getTime();
  const loginResponse = http.post(`${BASE_URL}/api/v1/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  const loginEnd = new Date().getTime();

  loginTrend.add(loginEnd - loginStart);

  const loginCheck = check(loginResponse, {
    'login status is 200': (r) => r.status === 200,
    'login response contains token': (r) => r.json().accessToken !== undefined,
    'login response time < 300ms': (r) => r.timings.duration < 300,
  });

  errorRate.add(!loginCheck);
  if (loginCheck) {
    successfulLogins.add(1);
  } else {
    failedLogins.add(1);
  }

  if (!loginCheck) {
    console.log(`Login failed for user ${testUser.username}`);
    sleep(Math.random() * 2 + 0.5);
    return;
  }

  const token = loginResponse.json().accessToken;
  const authHeaders = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };

  // Test 3: Get Current User
  const userStart = new Date().getTime();
  const userResponse = http.get(`${BASE_URL}/api/v1/users/me`, { headers: authHeaders });
  const userEnd = new Date().getTime();

  userRetrievalTrend.add(userEnd - userStart);

  const userCheck = check(userResponse, {
    'user endpoint status is 200': (r) => r.status === 200,
    'user response has username': (r) => r.json().username !== undefined,
    'user response time < 200ms': (r) => r.timings.duration < 200,
  });

  errorRate.add(!userCheck);

  // Test 4: Get All Users (Admin/Staff only)
  const allUsersResponse = http.get(`${BASE_URL}/api/v1/users`, { headers: authHeaders });
  const allUsersCheck = check(allUsersResponse, {
    'all users endpoint status is 200': (r) => r.status === 200,
    'all users response is array': (r) => Array.isArray(r.json()),
  });

  errorRate.add(!allUsersCheck);

  // Test 5: Get User by ID
  const getUserResponse = http.get(`${BASE_URL}/api/v1/users/1`, { headers: authHeaders });
  const getUserCheck = check(getUserResponse, {
    'get user by id status is 200': (r) => r.status === 200,
    'get user response has id': (r) => r.json().id !== undefined,
  });

  errorRate.add(!getUserCheck);

  // Test 6: Token Refresh
  const refreshPayload = JSON.stringify({
    refreshToken: loginResponse.json().refreshToken
  });

  const refreshResponse = http.post(`${BASE_URL}/api/v1/auth/refresh`, refreshPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const refreshCheck = check(refreshResponse, {
    'refresh status is 200': (r) => r.status === 200,
    'refresh response has new access token': (r) => r.json().accessToken !== undefined,
  });

  errorRate.add(!refreshCheck);

  // Simulate user think time
  sleep(Math.random() * 2 + 0.5); // 0.5-2.5 seconds
}

// Teardown function - runs after the test completes
export function teardown(data) {
  console.log('Performance test completed.');
  console.log(`Tested with ${data.users.length} users.`);
  console.log(`Successful logins: ${successfulLogins.values}`);
  console.log(`Failed logins: ${failedLogins.values}`);
  console.log(`Successful registrations: ${successfulRegistrations.values}`);
}