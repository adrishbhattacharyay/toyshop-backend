/**
 * Order Service — Load Performance Test
 *
 * Covers: create order, get order by ID, list user orders, cancel order
 * Target: API Gateway :8080
 *
 * Run:
 *   k6 run order-service-perf-test.js
 *   k6 run --env BASE_URL=http://staging:8080 order-service-perf-test.js
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
const createOrderDuration = new Trend('create_order_duration', true);
const getOrderDuration    = new Trend('get_order_duration', true);
const listOrdersDuration  = new Trend('list_orders_duration', true);
const ordersCreated       = new Counter('orders_created');
const ordersFailed        = new Counter('orders_failed');

// ---------------------------------------------------------------------------
// Test configuration
// ---------------------------------------------------------------------------
export const options = {
  stages: [
    { duration: '20s', target: 5  },
    { duration: '1m',  target: 30 },
    { duration: '30s', target: 50 },
    { duration: '20s', target: 0  },
  ],
  thresholds: {
    http_req_duration:     ['p(95)<1000', 'p(99)<2000'],
    http_req_failed:       ['rate<0.05'],
    errors:                ['rate<0.05'],
    create_order_duration: ['p(95)<1000'],
    get_order_duration:    ['p(95)<400'],
    list_orders_duration:  ['p(95)<500'],
  },
};

// ---------------------------------------------------------------------------
// Setup — create test users with known credentials
// ---------------------------------------------------------------------------
export function setup() {
  const users = [];
  for (let i = 0; i < 10; i++) {
    const u = registerUser(`ord${i}`);
    if (u) {
      const token = login(u.username, u.password);
      if (token) {
        // Fetch the user's ID from the profile endpoint
        const profileRes = http.get(
          `${BASE_URL}/api/v1/users/me`,
          { headers: authHeaders(token) }
        );
        const userId = profileRes.status === 200 ? profileRes.json('id') : null;
        users.push({ ...u, token, userId });
      }
    }
  }
  console.log(`[setup] ${users.length} order-test users ready`);
  return { users };
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function buildOrderPayload(userId) {
  // Product details are passed by the client — no Product Service call needed
  const products = [
    { productId: '00000000-0000-0000-0000-000000000001', productSku: 'TOY-001', productName: 'LEGO Classic', unitPrice: 29.99 },
    { productId: '00000000-0000-0000-0000-000000000002', productSku: 'TOY-002', productName: 'Barbie Doll',  unitPrice: 14.99 },
    { productId: '00000000-0000-0000-0000-000000000003', productSku: 'TOY-003', productName: 'Hot Wheels',   unitPrice: 4.99  },
  ];
  const item = products[Math.floor(Math.random() * products.length)];

  return {
    userId,
    items: [{
      productId:   item.productId,
      productSku:  item.productSku,
      productName: item.productName,
      quantity:    Math.floor(Math.random() * 3) + 1,
      unitPrice:   item.unitPrice,
    }],
    shippingAddress: '123 Perf Street, Load City, LC 10001',
    billingAddress:  '123 Perf Street, Load City, LC 10001',
    shippingMethod:  'STANDARD',
    paymentMethod:   'COD',
  };
}

// ---------------------------------------------------------------------------
// Main VU loop
// ---------------------------------------------------------------------------
export default function (data) {
  const { users } = data;
  if (!users || users.length === 0) return;

  const user = users[Math.floor(Math.random() * users.length)];
  if (!user.token || !user.userId) return;

  const hdrs = authHeaders(user.token);

  // --- 1. Create an order ---
  const createStart = Date.now();
  const createRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify(buildOrderPayload(user.userId)),
    { headers: hdrs, tags: { name: 'order_create' } }
  );
  createOrderDuration.add(Date.now() - createStart);

  const createOk = check(createRes, {
    'create order: 201':          (r) => r.status === 201,
    'create order: has orderNumber': (r) => r.json('orderNumber') !== undefined,
    'create order: status PENDING':  (r) => r.json('status') === 'PENDING',
    'create order: <1000ms':      (r) => r.timings.duration < 1000,
  });

  errorRate.add(!createOk);
  if (createOk) {
    ordersCreated.add(1);
  } else {
    ordersFailed.add(1);
    sleep(1);
    return;
  }

  const orderId = createRes.json('id');

  // --- 2. Get order by ID ---
  const getStart = Date.now();
  const getRes = http.get(
    `${BASE_URL}/api/v1/orders/${orderId}`,
    { headers: hdrs, tags: { name: 'order_get_by_id' } }
  );
  getOrderDuration.add(Date.now() - getStart);

  errorRate.add(!check(getRes, {
    'get order: 200':          (r) => r.status === 200,
    'get order: correct id':   (r) => r.json('id') === orderId,
    'get order: <400ms':       (r) => r.timings.duration < 400,
  }));

  // --- 3. List user's orders ---
  const listStart = Date.now();
  const listRes = http.get(
    `${BASE_URL}/api/v1/orders/user/${user.userId}`,
    { headers: hdrs, tags: { name: 'order_list_by_user' } }
  );
  listOrdersDuration.add(Date.now() - listStart);

  errorRate.add(!check(listRes, {
    'list orders: 200':       (r) => r.status === 200,
    'list orders: is array':  (r) => Array.isArray(r.json()),
    'list orders: <500ms':    (r) => r.timings.duration < 500,
  }));

  // --- 4. Cancel the order (30% of the time to simulate real behaviour) ---
  if (Math.random() < 0.3) {
    const cancelRes = http.post(
      `${BASE_URL}/api/v1/orders/${orderId}/cancel`,
      null,
      { headers: hdrs, tags: { name: 'order_cancel' } }
    );
    errorRate.add(!check(cancelRes, {
      'cancel order: 200': (r) => r.status === 200,
    }));
  }

  sleep(Math.random() * 2 + 0.5);
}

export function handleSummary(data) {
  return htmlReport(data, 'order-service-load');
}
