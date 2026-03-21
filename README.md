# ToyShop Backend - Microservices Suite

A complete E-Commerce Toy Shop backend built with Spring Boot microservices architecture.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway (8080)                        │
└─────────────────────────────────────────────────────────────────┘
         │                    │                    │
    ┌────▼────┐          ┌────▼────┐          ┌────▼────┐
    │  User   │          │ Product │          │  Order  │
    │ Service │          │ Service │          │ Service │
    │ (8081)  │          │ (8082)  │          │ (8083)  │
    └─────────┘          └─────────┘          └─────────┘
         │                    │                    │
    ┌────▼────┐          ┌────▼────┐          ┌────▼────┐
    │Payment  │          │Notification         │Service  │
    │Service  │          │Service  │          │Discovery│
    │ (8084)  │          │ (8085)  │          │ (8761)  │
    └─────────┘          └─────────┘          └─────────┘
         │                    │
    ┌────▼────────────────────▼────┐
    │  PostgreSQL, Redis, Kafka    │
    └──────────────────────────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| API Gateway | 8080 | Routing and load balancing |
| User Service | 8081 | Authentication and user management |
| Product Service | 8082 | Product catalog and inventory |
| Order Service | 8083 | Order processing and tracking |
| Payment Service | 8084 | Payment processing |
| Notification Service | 8085 | Email/SMS notifications |
| Service Discovery | 8761 | Eureka server |

## Features

- **Authentication**: JWT-based with role-based access control
- **Database**: PostgreSQL with TestContainers for testing
- **Caching**: Redis for performance optimization
- **Messaging**: Kafka for event-driven communication
- **Testing**: JUnit, Karate, k6, TestContainers
- **Observability**: Actuator, Prometheus, OpenTelemetry
- **API Documentation**: OpenAPI/Swagger

## Quick Start

```bash
# Build all services
mvn clean install

# Run with Docker Compose
docker-compose up -d

# Run locally (requires PostgreSQL, Redis, Kafka)
cd user-service && mvn spring-boot:run
```

## Testing

```bash
# Run all tests with coverage
mvn clean install

# Run specific service tests
cd user-service && mvn test

# Run Karate API tests
mvn test -Dkarate

# Run k6 performance tests
k6 run src/test/k6/performance-test.js
```

## Configuration

All services use `application.yml` for configuration. Key settings:
- Database connection: PostgreSQL on localhost:5432
- Redis: localhost:6379
- Kafka: localhost:9092
- Eureka: http://localhost:8761/eureka/

## API Documentation

- User Service: http://localhost:8081/swagger-ui.html
- Product Service: http://localhost:8082/swagger-ui.html
- Order Service: http://localhost:8083/swagger-ui.html
- Payment Service: http://localhost:8084/swagger-ui.html

## Health Checks

- Gateway: http://localhost:8080/actuator/health
- User Service: http://localhost:8081/actuator/health
- Eureka Dashboard: http://localhost:8761