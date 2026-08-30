/**
 * End-to-End Checkout Scenario — Full Purchase Journey
 *
 * Simulates the complete user flow that was reported as "slow":
 *   1. Register (new user) or Login (returning user)
 *   2. Browse product catalog
 *   3. Get product detail
 *   4. Create order
 *   5. Process payment
 *   6. Verify order status
 *
 * This is the most realistic test — it exercises every service through
 * the API Gateway in the same sequence a real user would.
 *
 * Run:
 *   k6 run scenarios/checkout-e2e.js
 *   k6 run --env SCENARIO=stress scenarios/checkout-e2e.js
 *
 * SCENARIO env var:
 *   smoke  — 1 VU, 3 iterations (default if service is cold)
 *   load   — 20 VUs, 2 min (default)
 *   stress — 50 VUs, 3 min with spike
 *   soak   — 10 VUs, 10 min
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, registerUser, login, authHeaders } from '../lib/auth.js';
import { htmlReport } from '../lib/report.js';

// ---------------------------------------------------------------------------
// Custom metrics — one per step so we can pinpoint the slow operation
// ---------------------------------------------------------------------------
const errorRate = new Rate('errors');

const loginDuration       = new Trend('e2e_login_duration',       true);
const browseProductsDuration = new Trend('e2e_browse_products_duration', true);
const getProductDuration  = new Trend('e2e_get_product_duration',  true);
const createOrderDuration = new Trend('e2e_create_order_duration', true);
const paymentDuration     = new Trend('e2e_payment_duration',      true);
const verifyOrderDuration = new Trend('e2e_verify_order_duration', true);
const fullJourneyDuration = new Trend('e2e_full_journey_duration', true);

const checkoutsCompleted = new Counter('checkouts_completed');
const checkoutsFailed    = new Counter('checkouts_failed');

// ---------------------------------------------------------------------------
// Scenario profiles
// ---------------------------------------------------------------------------
const SCENARIO = __ENV.SCENARIO || 'load';

const SCENARIOS = {
  smoke: {
    executor: 'per-vu-iterations',
    vus: 1,
    iterations: 3,
    maxDuration: '2m',
  },
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 10 },
      { duration: '1m',  target: 20 },
      { duration: '30s', target: 20 },
      { duration: '30s', target: 0  },
    ],
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 10 },
      { duration: '1m',  target: 50 },
      { duration: '30s', target: 80 }, // spike
      { duration: '30s', target: 50 },
      { duration: '30s', target: 0  },
    ],
  },
  soak: {
    executor: 'constant-vus',
    vus: 10,
    duration: '10m',
  },
};

export const options = {
  scenarios: {
    checkout: SCENARIOS[SCENARIO] || SCENARIOS.load,
  },
  thresholds: {
    // Overall HTTP
    http_req_duration:            ['p(95)<1500', 'p(99)<3000'],
    http_req_failed:              ['rate<0.05'],
    errors:                       ['rate<0.05'],
    // Per-step SLOs — these tell you exactly where the slowness is
    e2e_login_duration:           ['p(95)<600'],
    e2e_browse_products_duration: ['p(95)<500'],
    e2e_get_product_duration:     ['p(95)<400'],
    e2e_create_order_duration:    ['p(95)<1200'],
    e2e_payment_duration:         ['p(95)<1500'],
    e2e_verify_order_duration:    ['p(95)<400'],
    // Full journey end-to-end
    e2e_full_journey_duration:    ['p(95)<5000'],
  },
};

// ---------------------------------------------------------------------------
// Setup — pre-create users so VUs don't all register at t=0
// ---------------------------------------------------------------------------
export function setup() {
  const users = [];
  const count = SCENARIO === 'smoke' ? 2 : SCENARIO === 'soak' ? 15 : 20;

  for (let i = 0; i < count; i++) {
    const u = registerUser(`e2e${i}`);
    if (u) {
      // Resolve userId upfront
      const token = login(u.username, u.password);
      if (token) {
        const profileRes = http.get(
          `${BASE_URL}/api/v1/users/me`,
          { headers: authHeaders(token) }
        );
        const userId = profileRes.status === 200 ? profileRes.json('id') : null;
        users.push({ ...u, userId });
      }
    }
  }

  // Seed a product we can reliably reference
  const adminToken = login('admin', 'Test@123');
  let seededProductId = null;
  let seededProductPrice = 29.99;

  if (adminToken) {
    const sku = `E2E-PROD-${Date.now()}`;
    const prodRes = http.post(
      `${BASE_URL}/api/v1/products`,
      JSON.stringify({
        sku,
        name:          'E2E Test Toy',
        description:   'Seeded by k6 e2e checkout test',
        price:         '29.99',
        stockQuantity: 9999,
        category:      'Action Figures',
        brand:         'E2EBrand',
        status:        'ACTIVE',
      }),
      { headers: authHeaders(adminToken) }
    );
    if (prodRes.status === 201) {
      seededProductId    = prodRes.json('id');
      seededProductPrice = 29.99;
    }
  }

  console.log(`[setup] ${users.length} users ready. Product: ${seededProductId}`);
  return { users, seededProductId, seededProductPrice };
}

// ---------------------------------------------------------------------------
// Main VU — full checkout journey
// ---------------------------------------------------------------------------
export default function (data) {
  const { users, seededProductId, seededProductPrice } = data;
  if (!users || users.length === 0) return;

  const user = users[Math.floor(Math.random() * users.length)];
  if (!user.userId) return;

  const journeyStart = Date.now();
  let token, orderId, paymentId;

  // ── Step 1: Login ──────────────────────────────────────────────────────
  group('1_login', () => {
    const start = Date.now();
    const res = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ usernameOrEmail: user.username, password: user.password }),
      { headers: JSON_HEADERS, tags: { name: 'e2e_login' } }
    );
    loginDuration.add(Date.now() - start);

    const ok = check(res, {
      'login: 200':           (r) => r.status === 200,
      'login: accessToken':   (r) => r.json('accessToken') !== undefined,
    });
    errorRate.add(!ok);
    if (!ok) { checkoutsFailed.add(1); return; }
    token = res.json('accessToken');
  });

  if (!token) return;
  const hdrs = authHeaders(token);

  // ── Step 2: Browse product catalog ────────────────────────────────────
  group('2_browse_products', () => {
    const start = Date.now();
    const res = http.get(
      `${BASE_URL}/api/v1/products?page=0&size=20`,
      { headers: hdrs, tags: { name: 'e2e_browse_products' } }
    );
    browseProductsDuration.add(Date.now() - start);

    errorRate.add(!check(res, {
      'browse: 200': (r) => r.status === 200,
    }));
  });

  sleep(0.5); // user thinks

  // ── Step 3: View product detail ────────────────────────────────────────
  if (seededProductId) {
    group('3_view_product', () => {
      const start = Date.now();
      const res = http.get(
        `${BASE_URL}/api/v1/products/${seededProductId}`,
        { headers: hdrs, tags: { name: 'e2e_get_product' } }
      );
      getProductDuration.add(Date.now() - start);

      errorRate.add(!check(res, {
        'product detail: 200':    (r) => r.status === 200,
        'product detail: has id': (r) => r.json('id') !== undefined,
      }));
    });
  }

  sleep(0.3); // user adds to cart (client-side localStorage, no API call)

  // ── Step 4: Create order ───────────────────────────────────────────────
  group('4_create_order', () => {
    const start = Date.now();
    const res = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({
        userId: user.userId,
        items: [{
          productId:   seededProductId || '00000000-0000-0000-0000-000000000001',
          productSku:  'E2E-PROD',
          productName: 'E2E Test Toy',
          quantity:    1,
          unitPrice:   seededProductPrice,
        }],
        shippingAddress: '42 Checkout Ave, Perf City, PC 99999',
        billingAddress:  '42 Checkout Ave, Perf City, PC 99999',
        shippingMethod:  'STANDARD',
        paymentMethod:   'CARD',
      }),
      { headers: hdrs, tags: { name: 'e2e_create_order' } }
    );
    createOrderDuration.add(Date.now() - start);

    const ok = check(res, {
      'create order: 201':             (r) => r.status === 201,
      'create order: orderNumber':     (r) => r.json('orderNumber') !== undefined,
      'create order: PENDING status':  (r) => r.json('status') === 'PENDING',
      'create order: totalAmount > 0': (r) => parseFloat(r.json('totalAmount')) > 0,
    });
    errorRate.add(!ok);
    if (ok) orderId = res.json('id');
  });

  if (!orderId) { checkoutsFailed.add(1); return; }

  sleep(0.2); // user reviews order summary

  // ── Step 5: Process payment ────────────────────────────────────────────
  group('5_process_payment', () => {
    const start = Date.now();
    const res = http.post(
      `${BASE_URL}/api/v1/payments`,
      JSON.stringify({
        orderId,
        userId:        user.userId,
        amount:        (seededProductPrice * 1.18 + 5).toFixed(2), // approx with tax+shipping
        paymentMethod: 'CARD',
        currency:      'USD',
        cardNumber:    '4111111111111111',
        expiryDate:    '12/26',
        cvv:           '123',
      }),
      { headers: hdrs, tags: { name: 'e2e_payment' } }
    );
    paymentDuration.add(Date.now() - start);

    const ok = check(res, {
      'payment: 201':    (r) => r.status === 201,
      'payment: has id': (r) => r.json('id') !== undefined,
    });
    errorRate.add(!ok);
    if (ok) paymentId = res.json('id');
  });

  // ── Step 6: Verify order status ────────────────────────────────────────
  group('6_verify_order', () => {
    const start = Date.now();
    const res = http.get(
      `${BASE_URL}/api/v1/orders/${orderId}`,
      { headers: hdrs, tags: { name: 'e2e_verify_order' } }
    );
    verifyOrderDuration.add(Date.now() - start);

    errorRate.add(!check(res, {
      'verify order: 200':    (r) => r.status === 200,
      'verify order: has id': (r) => r.json('id') !== undefined,
    }));
  });

  // ── Record full journey time ───────────────────────────────────────────
  fullJourneyDuration.add(Date.now() - journeyStart);
  checkoutsCompleted.add(1);

  sleep(Math.random() * 2 + 1);
}

export function handleSummary(data) {
  return htmlReport(data, `checkout-e2e-${SCENARIO}`);
}
