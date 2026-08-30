/**
 * Product Service — Load Performance Test
 *
 * Covers: list products, get by ID, get categories, search/filter
 * Target: API Gateway :8080
 *
 * Run:
 *   k6 run product-service-perf-test.js
 *   k6 run --env BASE_URL=http://staging:8080 product-service-perf-test.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, adminLogin, authHeaders } from '../../../../user-service/src/test/k6/lib/auth.js';
import { htmlReport } from '../../../../user-service/src/test/k6/lib/report.js';

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const errorRate = new Rate('errors');
const listProductsDuration  = new Trend('list_products_duration', true);
const getProductDuration    = new Trend('get_product_duration', true);
const categoriesDuration    = new Trend('categories_duration', true);
const createProductDuration = new Trend('create_product_duration', true);

// ---------------------------------------------------------------------------
// Test configuration
// ---------------------------------------------------------------------------
export const options = {
  stages: [
    { duration: '20s', target: 10 },
    { duration: '1m',  target: 50 },
    { duration: '30s', target: 80 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_duration:       ['p(95)<600', 'p(99)<1200'],
    http_req_failed:         ['rate<0.05'],
    errors:                  ['rate<0.05'],
    // Product catalog is heavily cached — should be fast
    list_products_duration:  ['p(95)<400'],
    get_product_duration:    ['p(95)<300'],
    categories_duration:     ['p(95)<200'],
    create_product_duration: ['p(95)<800'],
  },
};

// ---------------------------------------------------------------------------
// Setup — get admin token + seed a few products to guarantee IDs exist
// ---------------------------------------------------------------------------
export function setup() {
  const token = adminLogin();
  if (!token) throw new Error('[setup] Admin login failed — is the service running?');

  const hdrs = authHeaders(token);
  const productIds = [];

  // Seed 5 test products
  const categories = ['Action Figures', 'Board Games', 'Puzzles', 'Dolls', 'LEGO'];
  for (let i = 0; i < 5; i++) {
    const sku = `PERF-SKU-${Date.now()}-${i}`;
    const res = http.post(
      `${BASE_URL}/api/v1/products`,
      JSON.stringify({
        sku,
        name:          `Perf Test Toy ${i}`,
        description:   'Created by k6 performance test',
        price:         (9.99 + i * 5).toFixed(2),
        stockQuantity: 100,
        category:      categories[i % categories.length],
        brand:         'PerfBrand',
        status:        'ACTIVE',
      }),
      { headers: hdrs }
    );
    if (res.status === 201) {
      const id = res.json('id');
      if (id) productIds.push(id);
    }
  }

  console.log(`[setup] Seeded ${productIds.length} products. Token acquired.`);
  return { token, productIds };
}

// ---------------------------------------------------------------------------
// Main VU loop — simulate a browsing customer (80% reads, 20% admin writes)
// ---------------------------------------------------------------------------
export default function (data) {
  const { token, productIds } = data;
  const hdrs = authHeaders(token);

  // --- 1. List products (paginated) ---
  const page = Math.floor(Math.random() * 5);
  const listStart = Date.now();
  const listRes = http.get(
    `${BASE_URL}/api/v1/products?page=${page}&size=20`,
    { headers: hdrs, tags: { name: 'product_list' } }
  );
  listProductsDuration.add(Date.now() - listStart);

  const listOk = check(listRes, {
    'list products: 200':        (r) => r.status === 200,
    'list products: has content': (r) => {
      const body = r.json();
      // Support both paginated {content:[]} and plain array responses
      return Array.isArray(body) || (body && (Array.isArray(body.content) || Array.isArray(body)));
    },
    'list products: <400ms':     (r) => r.timings.duration < 400,
  });
  errorRate.add(!listOk);

  // --- 2. Get categories ---
  const catStart = Date.now();
  const catRes = http.get(
    `${BASE_URL}/api/v1/products/categories`,
    { headers: hdrs, tags: { name: 'product_categories' } }
  );
  categoriesDuration.add(Date.now() - catStart);

  errorRate.add(!check(catRes, {
    'categories: 200':       (r) => r.status === 200,
    'categories: is array':  (r) => Array.isArray(r.json()),
    'categories: <200ms':    (r) => r.timings.duration < 200,
  }));

  // --- 3. Get product by ID (from seeded list) ---
  if (productIds && productIds.length > 0) {
    const id = productIds[Math.floor(Math.random() * productIds.length)];
    const getStart = Date.now();
    const getRes = http.get(
      `${BASE_URL}/api/v1/products/${id}`,
      { headers: hdrs, tags: { name: 'product_get_by_id' } }
    );
    getProductDuration.add(Date.now() - getStart);

    errorRate.add(!check(getRes, {
      'get product: 200':    (r) => r.status === 200,
      'get product: has id': (r) => r.json('id') !== undefined,
      'get product: <300ms': (r) => r.timings.duration < 300,
    }));
  }

  // --- 4. Admin: create a product (20% of VUs) ---
  if (__VU % 5 === 0) {
    const sku = `LOAD-${__VU}-${__ITER}-${Date.now()}`;
    const createStart = Date.now();
    const createRes = http.post(
      `${BASE_URL}/api/v1/products`,
      JSON.stringify({
        sku,
        name:          `Load Test Product ${__VU}-${__ITER}`,
        description:   'k6 load test product',
        price:         '19.99',
        stockQuantity: 50,
        category:      'Board Games',
        brand:         'LoadBrand',
        status:        'ACTIVE',
      }),
      { headers: hdrs, tags: { name: 'product_create' } }
    );
    createProductDuration.add(Date.now() - createStart);

    errorRate.add(!check(createRes, {
      'create product: 201': (r) => r.status === 201,
      'create product: has id': (r) => r.json('id') !== undefined,
    }));
  }

  sleep(Math.random() * 1 + 0.3);
}

export function handleSummary(data) {
  return htmlReport(data, 'product-service-load');
}
