# VolcanoToysForKids — Technical Guide

> Deep-dive into how the system works: request flows, authentication, data patterns, and design decisions.

---

## Table of Contents

1. [System Philosophy](#1-system-philosophy)
2. [Request Lifecycle — From Browser to Database](#2-request-lifecycle--from-browser-to-database)
3. [Authentication & Authorisation](#3-authentication--authorisation)
4. [Service Discovery & Load Balancing](#4-service-discovery--load-balancing)
5. [API Gateway — How Routing Works](#5-api-gateway--how-routing-works)
6. [User Service — Registration, Login, Token Issuance](#6-user-service--registration-login-token-issuance)
7. [Product Service — Catalog & Caching](#7-product-service--catalog--caching)
8. [Order Service — Lifecycle & Pricing](#8-order-service--lifecycle--pricing)
9. [Payment Service](#9-payment-service)
10. [Notification Service — Event-Driven Emails](#10-notification-service--event-driven-emails)
11. [Event-Driven Architecture with Kafka](#11-event-driven-architecture-with-kafka)
12. [Frontend Architecture](#12-frontend-architecture)
13. [Data Isolation — Database per Service](#13-data-isolation--database-per-service)
14. [Resilience & Error Handling](#14-resilience--error-handling)
15. [Testing Strategy](#15-testing-strategy)

---

## 1. System Philosophy

VolcanoToysForKids is built on three core principles:

**Loose coupling.** Each microservice owns its domain completely — its own database, its own models, its own business rules. No service reaches into another service's database. Cross-service communication happens either through the API Gateway (synchronous REST) or through Kafka (asynchronous events). This means a failure in the Notification Service does not affect the ability to place orders.

**Stateless services.** No service stores session state in memory. Authentication state lives in a JWT token the client holds. Cart state lives in the browser's localStorage. This makes every service instance interchangeable — you can scale horizontally without sticky sessions.

**Fail gracefully.** Kafka publish failures are caught and logged as warnings, not thrown as exceptions. The order is still created even if the event bus is temporarily unavailable. The user gets their order; the notification may be delayed.

---

## 2. Request Lifecycle — From Browser to Database

A typical `GET /api/v1/products` request travels through 5 layers:

```
Browser (localhost:5173)
  │  Vite dev proxy rewrites /api/* → http://localhost:8080
  ▼
API Gateway (localhost:8080)
  │  Spring Cloud Gateway receives the request
  │  Looks up "product-service" in Eureka registry
  │  LoadBalancer picks instance: localhost:8082
  ▼
Product Service (localhost:8082)
  │  Spring MVC dispatches to ProductController.getAllProducts()
  │  Checks Redis cache for "products:page:0"
  │  Cache miss → queries PostgreSQL toyshop_products
  │  Stores result in Redis with TTL 3600s
  ▼
PostgreSQL (localhost:5432/toyshop_products)
  │  SELECT * FROM products WHERE status='ACTIVE' ORDER BY created_at DESC
  ▼
Response bubbles back through the same chain
```

For authenticated requests (e.g. `POST /api/v1/orders`), the flow adds a JWT validation step at the service level — the gateway passes the `Authorization: Bearer <token>` header through unchanged, and the downstream service validates it.

---

## 3. Authentication & Authorisation

### Token Issuance

When a user logs in via `POST /api/v1/auth/login`:

1. User Service looks up the user by username or email
2. BCrypt compares the submitted password against the stored hash
3. On match, two JWTs are generated using `jjwt 0.12` with algorithm `HS384`:
   - **Access token** — expires in 24 hours, contains `sub: username`
   - **Refresh token** — expires in 7 days
4. Both tokens are returned in the response body
5. The frontend stores them in `localStorage`

### Token Validation

Each protected service validates the JWT independently — there is no central auth server. The secret key `toyshop-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256` is shared via application config.

### Role-Based Access

| Role | Permissions |
|------|-------------|
| `CUSTOMER` | Browse products, place orders, view own orders |
| `STAFF` | All customer permissions + manage products, view all orders |
| `ADMIN` | All staff permissions + delete products, manage users |

Endpoints annotated with `@PreAuthorize("hasRole('ADMIN')")` reject requests from lower roles with `403 Forbidden`.

### Token Refresh

The frontend `api.js` interceptor catches `401` responses (except from auth endpoints themselves) and automatically calls `POST /api/v1/auth/refresh` with the stored refresh token. If refresh succeeds, the original request is retried with the new access token. If refresh fails, the user is redirected to `/login`.

---

## 4. Service Discovery & Load Balancing

**Eureka Server** runs on port `8761`. Every microservice registers itself on startup with:

```yaml
eureka:
  instance:
    hostname: localhost
    instance-id: ${spring.application.name}:${server.port}
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
```

The API Gateway does **not** register itself (`register-with-eureka: false`) but does fetch the registry (`fetch-registry: true`) so it can resolve `lb://user-service` URIs.

**Spring Cloud LoadBalancer** (not Ribbon) handles the `lb://` URI resolution. It fetches the list of instances for a service name from Eureka and applies round-robin selection.

Key fix applied: services must register with `hostname: localhost` (not the Docker hostname `host.docker.internal`) so the gateway can actually reach them on the host machine.

---

## 5. API Gateway — How Routing Works

The gateway is a **reactive** Spring Cloud Gateway running on Netty (not Tomcat). This is critical — mixing `spring-boot-starter-web` (Tomcat) with the gateway causes routes to not load (routes count stays 0).

Route configuration in `application.yml`:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/auth/**,/api/v1/users/**
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/v1/products/**
```

The gateway's `SecurityConfig` uses `@EnableWebFluxSecurity` (reactive) and permits all requests — JWT validation is delegated to each downstream service, not done at the gateway level.

**CORS** is configured globally to allow `http://localhost:5173` (Vite dev server) with credentials.

---

## 6. User Service — Registration, Login, Token Issuance

### Registration

`POST /api/v1/auth/register` accepts:

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "Test@1234",
  "firstName": "John",
  "lastName": "Doe"
}
```

Password policy (enforced by `@Pattern` on `RegistrationRequest`):
- Minimum 8 characters
- At least one uppercase, one lowercase, one digit, one special character (`@$!%*?&`)

On success returns `201 Created` with the user profile (no token — user must login separately).

### Default Users

Seeded via `data.sql` on startup (using `ON CONFLICT DO UPDATE` so it's idempotent):

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Test@123` | `ADMIN` |
| `staff` | `Test@123` | `STAFF` |

### Kafka Events Published

Every user action publishes to the `user-events` topic:
`REGISTERED`, `LOGGED_IN`, `UPDATED`, `DELETED`, `PASSWORD_CHANGED`, `PASSWORD_RESET`

Kafka failures are non-fatal — wrapped in try/catch so a Kafka outage doesn't break login.

---

## 7. Product Service — Catalog & Caching

### Key Design Decisions

**UUID primary keys** — Products use `UUID` (not `Long`) as the primary key, generated by the database (`@GeneratedValue(strategy = GenerationType.UUID)`). This avoids sequential ID enumeration attacks and works well in distributed systems.

**JPA Auditing** — The `@CreatedDate` and `@LastModifiedDate` fields require `@EnableJpaAuditing` on the application class. Without it, inserts throw a 500 because the `created_at NOT NULL` constraint is violated.

**Unified filter endpoint** — `GET /api/v1/products` accepts optional `category` and `keyword` query params, routing to the appropriate repository method internally. This avoids the frontend needing to know about separate `/category/{cat}` endpoints.

### Category Filtering

```
GET /api/v1/products?category=Building+Blocks&page=0&size=20&sortBy=price&sortDir=asc
```

The controller checks params in order: `keyword` → `category` → all products.

---

## 8. Order Service — Lifecycle & Pricing

### Pricing Calculation

```
subtotal  = Σ (unitPrice × quantity - itemDiscount)
taxAmount = subtotal × 0.18   (18% GST)
shipping  = subtotal ≥ 500 ? 0 : 50
total     = subtotal + taxAmount + shipping - orderDiscount
```

### Order Number Generation

```java
"ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
// e.g. ORD-A3F7B2C1
```

### Product ID Type

Order items store `productId` as `String` (not `Long`) because Product Service uses UUID primary keys. This was a critical fix — the original `Long productId` caused validation failures when the frontend sent UUID strings.

### Kafka Events

Order events are published to `order-events` topic at each status transition. Failures are non-fatal (try/catch).

---

## 9. Payment Service

Payment Service is currently a stub implementation — it accepts payment requests and records them but does not integrate with a real payment gateway (Razorpay, Stripe, etc.).

Supported payment methods: `COD`, `UPI`, `CARD`

Payment status flow: `PENDING` → `COMPLETED` | `FAILED` | `REFUNDED`

When payment status changes to `COMPLETED`, the Order Service is notified via `payment-events` Kafka topic, which triggers the order status to move from `PENDING` to `CONFIRMED`.

---

## 10. Notification Service — Event-Driven Emails

The Notification Service consumes from three Kafka topics:
- `user-events` — welcome emails, password reset emails
- `order-events` — order confirmation, shipping updates
- `payment-events` — payment receipts

It uses Spring Mail (`spring-boot-starter-mail`) configured to connect to an SMTP server on `localhost:1025` (MailHog in development). In production this would point to SES, SendGrid, etc.

**Health check** — Kafka and mail health indicators are disabled in `application.yml` to prevent the service from reporting `DOWN` when the local SMTP/Kafka isn't running:

```yaml
management:
  health:
    kafka.enabled: false
    mail.enabled: false
```

---

## 11. Event-Driven Architecture with Kafka

### Why Kafka?

Synchronous REST calls between services create tight coupling — if the Notification Service is down, the Order Service would fail too. Kafka decouples them: the Order Service publishes an event and moves on; the Notification Service processes it whenever it's available.

### Topic Design

| Topic | Producers | Consumers | Key Events |
|-------|-----------|-----------|------------|
| `user-events` | User Service | Notification Service | REGISTERED, LOGGED_IN |
| `order-events` | Order Service | Notification Service | CREATED, CONFIRMED, SHIPPED |
| `payment-events` | Payment Service | Notification Service | COMPLETED, FAILED |

### Local Kafka Setup

The `infrastructure-only.yml` configures Kafka with two listeners:

```yaml
KAFKA_LISTENERS: PLAINTEXT_INTERNAL://0.0.0.0:29092,PLAINTEXT_EXTERNAL://0.0.0.0:9092
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT_INTERNAL://kafka:29092,PLAINTEXT_EXTERNAL://localhost:9092
```

`PLAINTEXT_INTERNAL` is for container-to-container communication. `PLAINTEXT_EXTERNAL` is for JVM services running on the host to connect via `localhost:9092`. Without this split, services running outside Docker cannot reach Kafka.

---

## 12. Frontend Architecture

### State Management

Redux Toolkit manages four slices:

| Slice | State | Persistence |
|-------|-------|-------------|
| `authSlice` | user, token, isAuthenticated | `localStorage` (token) |
| `productSlice` | items, categories, pagination | In-memory |
| `cartSlice` | items | `localStorage` (full cart) |
| `orderSlice` | items, currentOrder | In-memory |

### API Layer

`src/services/api.js` creates an Axios instance with:
- `baseURL: ''` — all paths are absolute (e.g. `/api/v1/products`)
- Request interceptor: attaches `Authorization: Bearer <token>` if token exists
- Response interceptor: on `401`, attempts token refresh — **skips auth endpoints** to avoid infinite loops

### Vite Proxy

In development, Vite proxies `/api/*` to `http://localhost:8080`, so the frontend never makes cross-origin requests:

```js
// vite.config.js
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true }
}
```

---

## 13. Data Isolation — Database per Service

Each service has its own PostgreSQL database. No shared tables, no cross-database joins.

| Service | Database | Key Tables |
|---------|----------|------------|
| User Service | `toyshop_users` | `users` |
| Product Service | `toyshop_products` | `products` |
| Order Service | `toyshop_orders` | `orders`, `order_items` |
| Payment Service | `toyshop_payments` | `payments` |

Cross-service data references use IDs only (e.g. `userId` in orders, `productId` in order items). If you need the full user object for an order, the frontend makes two separate API calls — one to the order service, one to the user service.

All databases use `spring.jpa.hibernate.ddl-auto: update` — Hibernate manages schema migrations automatically in development. In production this should be `validate` with Flyway/Liquibase handling migrations.

---

## 14. Resilience & Error Handling

### Non-Fatal Kafka

All `kafkaTemplate.send()` calls are wrapped in try/catch. A Kafka outage degrades gracefully — events are lost but the primary operation (login, order creation) succeeds.

### Global Exception Handlers

Each service has a `@RestControllerAdvice` that maps exceptions to HTTP status codes:

| Exception | HTTP Status |
|-----------|-------------|
| `UserNotFoundException` | 404 Not Found |
| `InvalidCredentialsException` | 401 Unauthorized |
| `UsernameAlreadyExistsException` | 409 Conflict |
| `ProductNotFoundException` | 404 Not Found |
| `SkuAlreadyExistsException` | 409 Conflict |
| `OrderNotFoundException` | 404 Not Found |
| `MethodArgumentNotValidException` | 400 Bad Request (with field errors) |
| `Exception` (catch-all) | 500 Internal Server Error |

### Frontend Error Handling

The `rejectWithValue` pattern in Redux async thunks extracts the error message from the API response and stores it in the slice's `error` field. Components render `{error && <div className="alert-danger">{error}</div>}`.

---

## 15. Testing Strategy

### Backend — Unit & Integration Tests

| Layer | Framework | Coverage |
|-------|-----------|---------|
| Service layer | JUnit 5 + Mockito | Business logic, edge cases |
| Repository layer | Spring Data Test + H2 | Query correctness |
| Controller layer | MockMvc + Spring Security Test | HTTP contract |
| Integration | Testcontainers (PostgreSQL) | Full stack with real DB |

Run with:
```bash
cd toyshop-backend
mvn test                    # unit tests only
mvn verify                  # unit + integration tests
```

### Frontend — Unit Tests

Jest + React Testing Library covers Redux slices and component rendering.

```bash
cd toyshop-frontend
npm test -- --run           # single pass (no watch mode)
```

### E2E — Playwright

35 tests across 6 spec files covering the full user journey:

| File | Tests | Coverage |
|------|-------|---------|
| `01-home.spec.js` | 5 | Hero, login link, products, categories |
| `02-auth.spec.js` | 7 | Login, register, validation, logout |
| `03-products.spec.js` | 7 | List, filter, search, sort, detail |
| `04-cart.spec.js` | 7 | Add, count, state, page, qty, remove |
| `05-checkout.spec.js` | 5 | Auth guard, form, payment, order |
| `06-orders.spec.js` | 4 | List, detail, filters, auth guard |

```bash
cd toyshop-frontend
npx playwright test                          # all 35 tests headless
npm run test:e2e:video                       # demo video (headed + recorded)
npx playwright show-report                   # HTML report
```

### Demo Video

A full walkthrough video is recorded via Playwright and committed to the repo:

```
toyshop-frontend/demo/volcanoToys-demo.webm
```

Covers: Home → Browse → Category filter → Product detail → Add to cart → Login → Checkout → Order confirmation → Order detail.
