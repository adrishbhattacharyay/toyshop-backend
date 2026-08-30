# ToyShop - Running Locally

Complete guide to run the ToyShop application on your local machine.

## Prerequisites

- Docker Desktop installed and running
- Java 17+
- Maven 3.9+
- Node.js 18+
- Git

---

## Step 1: Start Infrastructure Services

Open a terminal and run:

```bash
cd toyshop-backend
docker-compose -f infrastructure-only.yml up -d
```

This starts:
- PostgreSQL (port 5432)
- Redis (port 6379)
- Kafka + Zookeeper (port 9092)

**Verify:**
```bash
docker ps
```

You should see 4 containers running.

---

## Step 2: Start Backend Services

Open **7 separate terminal windows** and run each command.

> Start Service Discovery first and wait ~15 seconds before starting the others.

### Terminal 1 - Service Discovery (Eureka)
```bash
cd toyshop-backend/service-discovery
mvn spring-boot:run
```
**URL:** http://localhost:8761

### Terminal 2 - User Service
```bash
cd toyshop-backend/user-service
mvn spring-boot:run
```
**URL:** http://localhost:8081
**Swagger:** http://localhost:8081/swagger-ui.html

### Terminal 3 - Product Service
```bash
cd toyshop-backend/product-service
mvn spring-boot:run
```
**URL:** http://localhost:8082
**Swagger:** http://localhost:8082/swagger-ui.html

### Terminal 4 - Order Service
```bash
cd toyshop-backend/order-service
mvn spring-boot:run
```
**URL:** http://localhost:8083

### Terminal 5 - Payment Service
```bash
cd toyshop-backend/payment-service
mvn spring-boot:run
```
**URL:** http://localhost:8084

### Terminal 6 - Notification Service
```bash
cd toyshop-backend/notification-service
mvn spring-boot:run
```
**URL:** http://localhost:8085

### Terminal 7 - API Gateway (START LAST)
```bash
cd toyshop-backend/api-gateway
mvn spring-boot:run
```
**URL:** http://localhost:8080

---

## Step 3: Start Frontend

Open a new terminal:

```bash
cd toyshop-frontend
npm install
npm run dev
```

**URL:** http://localhost:5173

---

## Step 4: Verify All Services

### Check Eureka Dashboard
Open: http://localhost:8761

You should see these services registered:
- USER-SERVICE
- PRODUCT-SERVICE
- ORDER-SERVICE
- PAYMENT-SERVICE
- NOTIFICATION-SERVICE

---

## Credentials

### Infrastructure

| Service | Host | Port | Username | Password |
|---------|------|------|----------|----------|
| PostgreSQL | localhost | 5432 | toyshop | toyshop_secret |
| Redis | localhost | 6379 | - | (no password) |
| Kafka | localhost | 9092 | - | (no auth) |

### Connection Strings

```
PostgreSQL (users DB): jdbc:postgresql://localhost:5432/toyshop_users
PostgreSQL (products): jdbc:postgresql://localhost:5432/toyshop_products
PostgreSQL (orders):   jdbc:postgresql://localhost:5432/toyshop_orders
PostgreSQL (payments): jdbc:postgresql://localhost:5432/toyshop_payments
Redis:  redis://localhost:6379
Kafka:  localhost:9092
```

### Application

Default users created on first run:

| Username | Password | Role |
|----------|----------|------|
| admin | Test@123 | ADMIN |
| staff | Test@123 | STAFF |

You can also register new users via:
- **API:** POST http://localhost:8080/api/v1/auth/register
- **Frontend:** http://localhost:5173/register

> Note: Password policy requires uppercase, lowercase, number, and special character (e.g. `Test@123`)

---

## API Endpoints

### Authentication
```
POST /api/v1/auth/register   - body: {username, email, password, firstName, lastName}
POST /api/v1/auth/login      - body: {usernameOrEmail, password}
POST /api/v1/auth/refresh    - body: {refreshToken}
POST /api/v1/auth/password/reset/request - body: {email}
```

### Products
```
GET  /api/v1/products
GET  /api/v1/products/{id}
GET  /api/v1/products/categories
POST /api/v1/products
PUT  /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

### Orders
```
GET  /api/v1/orders
GET  /api/v1/orders/{id}
POST /api/v1/orders
PUT  /api/v1/orders/{id}/status
```

### Payments
```
POST /api/v1/payments
GET  /api/v1/payments/{id}
PUT  /api/v1/payments/{id}/refund
```

---

## Service URLs Summary

| Service | Port | URL | Purpose |
|---------|------|-----|---------|
| API Gateway | 8080 | http://localhost:8080 | Main entry point |
| User Service | 8081 | http://localhost:8081 | Authentication |
| Product Service | 8082 | http://localhost:8082 | Products |
| Order Service | 8083 | http://localhost:8083 | Orders |
| Payment Service | 8084 | http://localhost:8084 | Payments |
| Notification Service | 8085 | http://localhost:8085 | Notifications |
| Eureka Dashboard | 8761 | http://localhost:8761 | Service discovery |
| Frontend | 5173 | http://localhost:5173 | React app |
| MailHog (email UI) | 8025 | http://localhost:8025 | Catch all emails locally |
| PostgreSQL | 5432 | localhost:5432 | Database |
| Redis | 6379 | localhost:6379 | Cache |
| Kafka | 9092 | localhost:9092 | Events |

---

## Testing the Application

### Run Backend Tests
```bash
cd toyshop-backend
mvn test
```

### Run Frontend Tests
```bash
cd toyshop-frontend
npm test -- --run
```

### Build for Production
```bash
# Backend
cd toyshop-backend
mvn clean install -DskipITs

# Frontend
cd toyshop-frontend
npm run build
```

---

## Stopping Services

### Stop Docker Infrastructure
```bash
cd toyshop-backend
docker-compose -f infrastructure-only.yml down
```

### Stop All Services
Press `Ctrl+C` in each terminal window.

### Stop Docker and Remove Data
```bash
cd toyshop-backend
docker-compose -f infrastructure-only.yml down -v
```

---

## Troubleshooting

### Port Already in Use
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Docker Not Running
Start Docker Desktop and wait for "Docker Desktop is running"

### Maven Build Issues
```bash
mvn clean
mvn dependency:resolve
```

### Frontend Issues
```bash
cd toyshop-frontend
rm -rf node_modules package-lock.json
npm install
```

### Services Registering with Wrong Hostname
If services show `host.docker.internal` in Eureka instead of `localhost`, ensure each service's `application.yml` has:
```yaml
eureka:
  instance:
    hostname: localhost
    prefer-ip-address: false
```

### API Gateway Returns 503
Ensure all backend services are registered in Eureka before starting the gateway. Check http://localhost:8761 first.

### Login Returns 401
The default users (admin/staff) are seeded via `data.sql` on startup. If the DB already existed with a bad hash, run:
```sql
-- Connect to toyshop_users DB and reset password manually
UPDATE users SET password='$2a$12$N1JkZN7QH/aL/f.VT6tOoewYKcZymunVgkilN6cb0agHFaE2aaH1.' WHERE username IN ('admin','staff');
```

---

## Quick Reference Commands

```bash
# Start infrastructure
cd toyshop-backend && docker-compose -f infrastructure-only.yml up -d

# Check status
docker ps

# View logs
docker-compose -f infrastructure-only.yml logs -f

# Stop infrastructure
docker-compose -f infrastructure-only.yml down
```

---

## Next Steps

After running locally, you can:
- Test the API using Swagger UI
- Register a new user
- Browse products
- Create orders
- Run full test suite
- Deploy to production