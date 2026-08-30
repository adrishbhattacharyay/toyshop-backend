/**
 * Payment Service — Load Performance Test
 *
 * Covers: initiate payment, get payment by ID
 * Target: API Gateway :8080
 *
 * Note: Payment creation requires a valid orderId. This test creates
 * orders first via the Order Service, then processes payments for them.
 *
 * Run:
 *   k6 run payment-service-perf-test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, registerUser, login, authHeaders } from '../../../../user-service/src/test/k6/lib/auth.js';
import { htmlReport } from '../../../../user-service/src/test/k6/lib/report.js';

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const errorRate = new Rate('errors');
const createPaymentDuration = new Trend('create_payment_duration', true);
const getPaymentDuration    = new Trend('get_payment_duration', true);
const paymentsCompleted     = new Counter('payments_completed');
const paymentsFailed        = new Counter('payments_failed');

// ---------------------------------------------------------------------------
// Test configuration
// ---------------------------------------------------------------------------
export const options = {
  stages: [
    { duration: '20s', target: 5  },
    { duration: '1m',  target: 25 },
    { duration: '30s', target: 40 },
    { duration: '20s', target: 0  },
  ],
  thresholds: {
    http_req_duration:       ['p(95)<1200', 'p(99)<2500'],
    http_req_failed:         ['rate<0.05'],
    errors:                  ['rate<0.05'],
    create_payment_duration: ['p(95)<1200'],
    get_payment_duration:    ['p(95)<400'],
  },
};

// ---------------------------------------------------------------------------
// Setup — create users and pre-create orders to pay for
// ---------------------------------------------------------------------------
export function setup() {
  const sessions = [];

  for (let i = 0; i < 8; i++) {
    const u = registerUser(`pay${i}`);
    if (!u) continue;

    const token = login(u.username, u.password);
    if (!token) continue;

    const hdrs = authHeaders(token);

    // Get userId
    const profileRes = http.get(`${BASE_URL}/api/v1/users/me`, { headers: hdrs });
    if (profileRes.status !== 200) continue;
    const userId = profileRes.json('id');

    // Create an order to pay for
    const orderRes = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({
        userId,
        items: [{
          productId:   '00000000-0000-0000-0000-000000000001',
          productSku:  'TOY-001',
          productName: 'LEGO Classic',
          quantity:    1,
          unitPrice:   29.99,
        }],
        shippingAddress: '1 Payment Lane, Test City',
        shippingMethod:  'STANDARD',
        paymentMethod:   'CARD',
      }),
      { headers: hdrs }
    );

    if (orderRes.status !== 201) continue;

    sessions.push({
      token,
      userId,
      orderId:     orderRes.json('id'),
      totalAmount: orderRes.json('totalAmount'),
    });
  }

  console.log(`[setup] ${sessions.length} payment sessions ready`);
  return { sessions };
}

// ---------------------------------------------------------------------------
// Main VU loop
// ---------------------------------------------------------------------------
export default function (data) {
  const { sessions } = data;
  if (!sessions || sessions.length === 0) return;

  const session = sessions[Math.floor(Math.random() * sessions.length)];
  const hdrs = authHeaders(session.token);

  // --- 1. Initiate payment ---
  const createStart = Date.now();
  const createRes = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({
      orderId:       session.orderId,
      userId:        session.userId,
      amount:        session.totalAmount,
      paymentMethod: 'CARD',
      currency:      'USD',
      cardNumber:    '4111111111111111',
      expiryDate:    '12/26',
      cvv:           '123',
    }),
    { headers: hdrs, tags: { name: 'payment_create' } }
  );
  createPaymentDuration.add(Date.now() - createStart);

  const createOk = check(createRes, {
    'create payment: 201':          (r) => r.status === 201,
    'create payment: has id':       (r) => r.json('id') !== undefined,
    'create payment: <1200ms':      (r) => r.timings.duration < 1200,
  });

  errorRate.add(!createOk);
  if (createOk) {
    paymentsCompleted.add(1);
  } else {
    paymentsFailed.add(1);
    sleep(1);
    return;
  }

  const paymentId = createRes.json('id');

  // --- 2. Get payment by ID ---
  const getStart = Date.now();
  const getRes = http.get(
    `${BASE_URL}/api/v1/payments/${paymentId}`,
    { headers: hdrs, tags: { name: 'payment_get_by_id' } }
  );
  getPaymentDuration.add(Date.now() - getStart);

  errorRate.add(!check(getRes, {
    'get payment: 200':        (r) => r.status === 200,
    'get payment: correct id': (r) => r.json('id') === paymentId,
    'get payment: <400ms':     (r) => r.timings.duration < 400,
  }));

  sleep(Math.random() * 2 + 1);
}

export function handleSummary(data) {
  return htmlReport(data, 'payment-service-load');
}
