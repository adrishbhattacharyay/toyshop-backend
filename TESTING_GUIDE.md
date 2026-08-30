# ToyShop Application - Local Testing Guide

This guide covers how to test both the frontend and backend components of the ToyShop application locally.

## Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.9+
- npm or yarn

---

## Starting Backend Services Locally (Without Docker)

### Startup Sequence

**IMPORTANT:** Services must be started in this exact order:

```
1. PostgreSQL     → Database (port 5432)
2. Redis          → Cache (port 6379)
3. Zookeeper      → Kafka coordination (port 2181)
4. Kafka          → Event streaming (port 9092)
5. Eureka         → Service Discovery (port 8761)
6. User Service   → User management (port 8081)
7. Product Service→ Product catalog (port 8082)
8. Order Service  → Order processing (port 8083)
9. Payment Service→ Payments (port 8084)
10. Notification  → Notifications (port 8085)
11. API Gateway   → Entry point (port 8080)
```

### Step 1: Start Infrastructure Services

**Start PostgreSQL:**
```bash
# Option A: Using Docker
docker run -d --name toyshop-postgres \
  -e POSTGRES_USER=toyshop \
  -e POSTGRES_PASSWORD=toyshop_secret \
  -e POSTGRES_DB=toyshop \
  -p 5432:5432 \
  postgres:15-alpine

# Option B: If PostgreSQL is installed locally
# Ensure it's running on port 5432
```

**Start Redis:**
```bash
# Using Docker
docker run -d --name toyshop-redis \
  -p 6379:6379 \
  redis:7-alpine

# Or ensure local Redis is running
```

**Start Kafka (requires Zookeeper):**
```bash
# Start Zookeeper
docker run -d --name toyshop-zookeeper \
  -p 2181:2181 \
  confluentinc/cp-zookeeper:7.5.0

# Start Kafka
docker run -d --name toyshop-kafka \
  -p 9092:9092 \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_ZOOKEEPER_CONNECT=localhost:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  confluentinc/cp-kafka:7.5.0
```

### Step 2: Start Service Discovery (Eureka)

```bash
cd toyshop-backend/service-discovery
mvn spring-boot:run
```
**URL:** http://localhost:8761

Wait until you see "Started Eureka Server" in the logs before proceeding.

### Step 3: Start Microservices (in order)

**User Service:**
```bash
cd toyshop-backend/user-service
mvn spring-boot:run
```
**URL:** http://localhost:8081
**API Docs:** http://localhost:8081/swagger-ui.html

**Product Service:**
```bash
cd toyshop-backend/product-service
mvn spring-boot:run
```
**URL:** http://localhost:8082
**API Docs:** http://localhost:8082/swagger-ui.html

**Order Service:**
```bash
cd toyshop-backend/order-service
mvn spring-boot:run
```
**URL:** http://localhost:8083

**Payment Service:**
```bash
cd toyshop-backend/payment-service
mvn spring-boot:run
```
**URL:** http://localhost:8084

**Notification Service:**
```bash
cd toyshop-backend/notification-service
mvn spring-boot:run
```
**URL:** http://localhost:8085

**API Gateway (START LAST):**
```bash
cd toyshop-backend/api-gateway
mvn spring-boot:run
```
**URL:** http://localhost:8080

### Step 4: Verify All Services Are Running

Check Eureka Dashboard: http://localhost:8761

You should see all services registered:
- TOYSHOP-USER-SERVICE
- TOYSHOP-PRODUCT-SERVICE
- TOYSHOP-ORDER-SERVICE
- TOYSHOP-PAYMENT-SERVICE
- TOYSHOP-NOTIFICATION-SERVICE
- API-GATEWAY

---

## Quick Start Script (Windows)

Save as `start-services.bat` in `toyshop-backend` directory:

```batch
@echo off
echo Starting ToyShop Infrastructure...
docker run -d --name toyshop-postgres -e POSTGRES_USER=toyshop -e POSTGRES_PASSWORD=toyshop_secret -e POSTGRES_DB=toyshop -p 5432:5432 postgres:15-alpine
docker run -d --name toyshop-redis -p 6379:6379 redis:7-alpine
docker run -d --name toyshop-zookeeper -p 2181:2181 confluentinc/cp-zookeeper:7.5.0
timeout /t 5 /nobreak
docker run -d --name toyshop-kafka -p 9092:9092 -e KAFKA_BROKER_ID=1 -e KAFKA_ZOOKEEPER_CONNECT=localhost:2181 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 confluentinc/cp-kafka:7.5.0

echo Starting Services...
start "Eureka" cmd /k cd service-discovery && mvn spring-boot:run
timeout /t 30 /nobreak
start "User Service" cmd /k cd user-service && mvn spring-boot:run
timeout /t 20 /nobreak
start "Product Service" cmd /k cd product-service && mvn spring-boot:run
timeout /t 20 /nobreak
start "Order Service" cmd /k cd order-service && mvn spring-boot:run
timeout /t 20 /nobreak
start "Payment Service" cmd /k cd payment-service && mvn spring-boot:run
timeout /t 20 /nobreak
start "Notification Service" cmd /k cd notification-service && mvn spring-boot:run
timeout /t 20 /nobreak
start "API Gateway" cmd /k cd api-gateway && mvn spring-boot:run

echo All services starting...
echo Eureka Dashboard: http://localhost:8761
echo API Gateway: http://localhost:8080
pause
```

---

## Starting with Docker Compose (Easier)

```bash
cd toyshop-backend
docker-compose up -d
```

This starts ALL services automatically:
- PostgreSQL (5432)
- Redis (6379)
- Kafka (9092)
- Zookeeper (2181)
- Eureka (8761)
- All microservices (8080-8085)
- Mailhog for email testing (8025)

**Check status:**
```bash
docker-compose ps
```

**View logs:**
```bash
docker-compose logs -f
```

**Stop all services:**
```bash
docker-compose down
```

---

## Backend Testing (toyshop-backend)

### Run Unit Tests

```bash
cd toyshop-backend
mvn test
```

This runs all unit tests for:
- User Service (28 tests)
- Product Service
- All controllers

**Expected Result:** All tests should pass

### Run Tests with Coverage

```bash
mvn test jacoco:report
```

Reports generated at: `target/site/jacoco/index.html`

### Run Integration Tests (Requires Docker)

```bash
mvn verify
```

**Note:** Integration tests require Docker to be running for TestContainers.

### Build the Application

```bash
mvn clean install -DskipITs
```

This compiles, tests, and packages all microservices.

---

## Frontend Testing (toyshop-frontend)

### 1. Navigate to Frontend Directory

```bash
cd toyshop-frontend
```

### 2. Install Dependencies

```bash
npm install
```

### 3. Run Unit Tests

```bash
npm test
```

This runs Jest tests for:
- Cart slice (6 tests)
- Product slice (8 tests)

**Expected Result:** 14/14 tests passing

### 4. Run Tests with Coverage

```bash
npm run test:coverage
```

Reports generated at: `coverage/index.html`

### 5. Run Tests in Watch Mode

```bash
npm run test:watch
```

### 6. Lint the Code

```bash
npm run lint
```

### 7. Fix Lint Issues

```bash
npm run lint:fix
```

### 8. Start Development Server

```bash
npm run dev
```

Runs on: http://localhost:5173

### 9. Build for Production

```bash
npm run build
```

Build output at: `dist/`

### 10. Preview Production Build

```bash
npm run preview
```

---

## End-to-End Testing (Frontend)

### 1. Install Cypress

```bash
npm install
```

### 2. Run E2E Tests (Headless)

```bash
npm run test:e2e
```

### 3. Open Cypress UI

```bash
npm run test:e2e:open
```

---

## Testing with External Services

### Configure Backend URL

Create `.env` in frontend directory:

```env
VITE_API_URL=http://localhost:8080/api/v1
```

### Test API Endpoints

**User Service Endpoints:**
- POST http://localhost:8080/api/v1/auth/register
- POST http://localhost:8080/api/v1/auth/login
- POST http://localhost:8080/api/v1/auth/refresh

**Product Service Endpoints:**
- GET http://localhost:8081/api/v1/products
- GET http://localhost:8081/api/v1/products/{id}
- GET http://localhost:8081/api/v1/products/categories

### Test with Swagger UI

Access Swagger documentation at:
- User Service: http://localhost:8080/swagger-ui.html
- Product Service: http://localhost:8081/swagger-ui.html

---

## Performance Testing

### k6 Test Suite Overview

The suite is distributed across each service's `src/test/k6/` folder and targets the
**API Gateway at `:8080`** — the real production path through Eureka and the load balancer.

| File | What it tests | Scenario |
|------|--------------|---------|
| `user-service/src/test/k6/user-service-perf-test.js` | Register, login, profile, refresh | Smoke (1 VU) |
| `user-service/src/test/k6/performance-test.js` | Auth flows under load | Load (50 VUs) |
| `product-service/src/test/k6/product-service-perf-test.js` | Browse catalog, get product, create product | Load (80 VUs) |
| `order-service/src/test/k6/order-service-perf-test.js` | Create order, get order, list, cancel | Load (50 VUs) |
| `payment-service/src/test/k6/payment-service-perf-test.js` | Initiate payment, get payment | Load (40 VUs) |
| `user-service/src/test/k6/scenarios/checkout-e2e.js` | **Full purchase journey** (login → browse → order → pay → verify) | smoke / load / stress / soak |

A shared library lives in `user-service/src/test/k6/lib/`:
- `auth.js` — login/register helpers, `adminLogin()`
- `report.js` — `handleSummary()` that writes JSON + HTML reports to `reports/`

### Prerequisites

Install k6: https://k6.io/docs/get-started/installation/

```bash
# Windows (winget)
winget install k6 --source winget

# macOS
brew install k6

# Linux
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

### Run Tests

**All services (recommended first run):**
```bash
# Windows
run-perf-tests.bat

# Linux / macOS
chmod +x run-perf-tests.sh && ./run-perf-tests.sh
```

**Smoke test only (quick sanity check):**
```bash
run-perf-tests.bat smoke
./run-perf-tests.sh smoke
```

**Single service:**
```bash
run-perf-tests.bat products
run-perf-tests.bat orders
run-perf-tests.bat payments
```

**End-to-end checkout (the most realistic test):**
```bash
# Load scenario (default)
run-perf-tests.bat e2e

# Stress scenario — spikes to 80 VUs
run-perf-tests.bat e2e stress

# Soak test — 10 VUs for 10 minutes
run-perf-tests.bat e2e soak
```

**Run a single file directly:**
```bash
k6 run user-service/src/test/k6/scenarios/checkout-e2e.js
k6 run --env BASE_URL=http://staging:8080 --env SCENARIO=stress \
       user-service/src/test/k6/scenarios/checkout-e2e.js
```

### Reports

After each run, reports are written to `reports/` in the project root:

```
reports/
  user-service-smoke-report.html
  user-service-smoke-summary.json
  user-service-load-report.html
  product-service-load-report.html
  order-service-load-report.html
  payment-service-load-report.html
  checkout-e2e-load-report.html
```

Open any `.html` file in a browser for a visual dashboard showing:
- Pass/fail status for all thresholds
- p(95) / p(99) / avg / max per operation
- Per-step breakdown (login, browse, create order, payment, verify)
- Error rate

### SLO Thresholds

| Operation | p(95) target |
|-----------|-------------|
| Login | < 500 ms |
| Browse products | < 400 ms |
| Get product detail | < 300 ms |
| Create order | < 1 000 ms |
| Process payment | < 1 200 ms |
| Full checkout journey | < 5 000 ms |
| Error rate (all) | < 5% |

---

## Troubleshooting

### Backend Issues

**Port Already in Use:**
```bash
# Find process using port
netstat -ano | findstr :8080
# Kill process
taskkill /PID <PID> /F
```

**Maven Build Failures:**
```bash
mvn clean
mvn dependency:resolve
mvn test
```

### Frontend Issues

**Node Module Issues:**
```bash
rm -rf node_modules package-lock.json
npm install
```

**Test Failures:**
```bash
npm test -- --verbose
```

---

## Test Results Summary

### Backend
- Unit Tests: 28 passing
- Integration Tests: Skipped (requires Docker)
- Build Status: ✅ Success

### Frontend
- Unit Tests: 14 passing
- E2E Tests: Available via Cypress
- Build Status: ✅ Success

---

## Quick Test Commands

```bash
# Backend - Full test suite
cd toyshop-backend && mvn clean test

# Frontend - Full test suite
cd toyshop-frontend && npm test && npm run build

# Both together
cd toyshop-backend && mvn clean install -DskipITs
cd toyshop-frontend && npm ci && npm test
```

---

## Service URLs Summary

| Service | Port | URL | Purpose |
|---------|------|-----|---------|
| API Gateway | 8080 | http://localhost:8080 | Main entry point |
| User Service | 8081 | http://localhost:8081 | Authentication & user management |
| Product Service | 8082 | http://localhost:8082 | Product catalog |
| Order Service | 8083 | http://localhost:8083 | Order processing |
| Payment Service | 8084 | http://localhost:8084 | Payment processing |
| Notification Service | 8085 | http://localhost:8085 | Email/push notifications |
| Eureka Dashboard | 8761 | http://localhost:8761 | Service discovery |
| PostgreSQL | 5432 | localhost:5432 | Database |
| Redis | 6379 | localhost:6379 | Cache |
| Kafka | 9092 | localhost:9092 | Event streaming |
| Frontend Dev | 5173 | http://localhost:5173 | React development server |