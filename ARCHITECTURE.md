# ToyShop Backend - Architecture Documentation

> Microservices-based e-commerce platform built with Spring Boot, Spring Cloud, and event-driven architecture.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [High-Level Design (HLD)](#high-level-design)
3. [Architecture Diagram](#architecture-diagram)
4. [Service Interaction Flow](#service-interaction-flow)
5. [Data Architecture](#data-architecture)
6. [Event-Driven Architecture](#event-driven-architecture)
7. [Security Architecture](#security-architecture)
8. [Infrastructure Diagram (Local)](#infrastructure-diagram-local)
9. [Infrastructure Diagram (Cloud / Production)](#infrastructure-diagram-cloud--production)
10. [Deployment Architecture (Kubernetes)](#deployment-architecture-kubernetes)
11. [Technology Stack](#technology-stack)

---

## System Overview

ToyShop is a cloud-native e-commerce backend built as a suite of independently deployable microservices. Each service owns its domain, its database, and communicates either synchronously via REST (through the API Gateway) or asynchronously via Kafka events.

```
Client (React SPA)
      │
      ▼
API Gateway  ──►  Service Discovery (Eureka)
      │
      ├──► User Service      (auth, JWT, profiles)
      ├──► Product Service   (catalog, inventory, images)
      ├──► Order Service     (cart, orders, fulfillment)
      └──► Payment Service   (transactions, refunds)
                                    │
                              Kafka Events
                                    │
                             Notification Service
                             (email, push alerts)
```

---

## High-Level Design

### HLD - Component View

```mermaid
graph TB
    subgraph Client["Client Layer"]
        FE["React Frontend\nhttp://localhost:5173"]
    end

    subgraph Gateway["Gateway Layer"]
        GW["API Gateway\nSpring Cloud Gateway\n:8080"]
        SD["Service Discovery\nNetflix Eureka\n:8761"]
    end

    subgraph Services["Microservices Layer"]
        US["User Service\n:8081\nAuth · JWT · Profiles"]
        PS["Product Service\n:8082\nCatalog · Inventory"]
        OS["Order Service\n:8083\nOrders · Cart"]
        PMS["Payment Service\n:8084\nTransactions · Refunds"]
        NS["Notification Service\n:8085\nEmail · Alerts"]
    end

    subgraph Messaging["Async Messaging"]
        KF["Apache Kafka\n:9092"]
    end

    subgraph Storage["Data Layer"]
        PG1[("PostgreSQL\ntoyshop_users")]
        PG2[("PostgreSQL\ntoyshop_products")]
        PG3[("PostgreSQL\ntoyshop_orders")]
        PG4[("PostgreSQL\ntoyshop_payments")]
        RD[("Redis Cache\n:6379")]
    end

    FE -->|"HTTP/REST"| GW
    GW -->|"lb:// service discovery"| SD
    GW -->|"/api/v1/auth/**\n/api/v1/users/**"| US
    GW -->|"/api/v1/products/**"| PS
    GW -->|"/api/v1/orders/**"| OS
    GW -->|"/api/v1/payments/**"| PMS

    US -->|"user-events"| KF
    OS -->|"order-events"| KF
    PMS -->|"payment-events"| KF
    KF -->|"consume events"| NS

    US --- PG1
    PS --- PG2
    OS --- PG3
    PMS --- PG4
    US --- RD
    PS --- RD
```

---

## Architecture Diagram

### Detailed Service Architecture

```mermaid
graph LR
    subgraph External["External"]
        Browser["Browser / Mobile"]
    end

    subgraph EdgeLayer["Edge Layer"]
        GW["🔀 API Gateway\nSpring Cloud Gateway\nNetty · Reactive\nPort 8080"]
        SD["🔍 Eureka Server\nService Registry\nPort 8761"]
    end

    subgraph CoreServices["Core Microservices"]
        direction TB
        US["👤 User Service\nSpring Boot · JPA\nBCrypt · JWT\nPort 8081"]
        PS["📦 Product Service\nSpring Boot · JPA\nS3 Images\nPort 8082"]
        OS["🛒 Order Service\nSpring Boot · JPA\nPort 8083"]
        PMS["💳 Payment Service\nSpring Boot · JPA\nPort 8084"]
        NS["🔔 Notification Service\nSpring Boot · Kafka\nSMTP Mail\nPort 8085"]
    end

    subgraph Infra["Infrastructure"]
        direction TB
        PG[("🐘 PostgreSQL 15\n4 Databases\nPort 5432")]
        RD[("⚡ Redis 7\nCache · Sessions\nPort 6379")]
        KF[("📨 Kafka\nEvent Bus\nPort 9092")]
        ZK["Zookeeper\nPort 2181"]
    end

    Browser -->|HTTPS| GW
    GW <-->|"Register / Discover"| SD
    US <-->|"Register"| SD
    PS <-->|"Register"| SD
    OS <-->|"Register"| SD
    PMS <-->|"Register"| SD

    GW -->|"Route"| US
    GW -->|"Route"| PS
    GW -->|"Route"| OS
    GW -->|"Route"| PMS

    US -->|"Read/Write"| PG
    PS -->|"Read/Write"| PG
    OS -->|"Read/Write"| PG
    PMS -->|"Read/Write"| PG

    US -->|"Cache"| RD
    PS -->|"Cache"| RD

    US -->|"Publish"| KF
    OS -->|"Publish"| KF
    PMS -->|"Publish"| KF
    KF -->|"Consume"| NS
    ZK -->|"Coordinate"| KF
```

---

## Service Interaction Flow

### User Login Flow

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend :5173
    participant GW as API Gateway :8080
    participant SD as Eureka :8761
    participant US as User Service :8081
    participant RD as Redis Cache

    User->>FE: Enter credentials
    FE->>GW: POST /api/v1/auth/login
    GW->>SD: Lookup USER-SERVICE
    SD-->>GW: Return instance (localhost:8081)
    GW->>US: Forward request
    US->>US: Validate credentials (BCrypt)
    US->>RD: Cache user session
    US-->>GW: JWT access + refresh token
    GW-->>FE: 200 OK + tokens
    FE->>FE: Store token in localStorage
```

### Place Order Flow

```mermaid
sequenceDiagram
    actor User
    participant GW as API Gateway
    participant OS as Order Service :8083
    participant PS as Product Service :8082
    participant PMS as Payment Service :8084
    participant KF as Kafka
    participant NS as Notification Service

    User->>GW: POST /api/v1/orders (JWT)
    GW->>OS: Forward + validate JWT
    OS->>PS: Check product availability
    PS-->>OS: Stock confirmed
    OS->>OS: Create order (PENDING)
    OS->>PMS: Initiate payment
    PMS->>PMS: Process transaction
    PMS->>KF: Publish payment-events (COMPLETED)
    PMS-->>OS: Payment confirmed
    OS->>KF: Publish order-events (CONFIRMED)
    KF->>NS: Consume order-events
    NS->>NS: Send confirmation email
    OS-->>GW: 201 Created (order details)
    GW-->>User: Order confirmed
```

---

## Data Architecture

### Database-per-Service Pattern

```mermaid
erDiagram
    USER-SERVICE ||--o{ USERS : owns
    USERS {
        bigint id PK
        string username
        string email
        string password
        string role
        boolean enabled
        timestamp created_at
        timestamp last_login_at
    }

    PRODUCT-SERVICE ||--o{ PRODUCTS : owns
    PRODUCTS {
        bigint id PK
        string name
        string sku
        decimal price
        int stock_quantity
        string category
        string image_url
    }

    ORDER-SERVICE ||--o{ ORDERS : owns
    ORDERS {
        bigint id PK
        bigint user_id FK
        string status
        decimal total_amount
        timestamp created_at
    }

    ORDER-SERVICE ||--o{ ORDER_ITEMS : owns
    ORDER_ITEMS {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        int quantity
        decimal unit_price
    }

    PAYMENT-SERVICE ||--o{ PAYMENTS : owns
    PAYMENTS {
        bigint id PK
        bigint order_id FK
        string status
        decimal amount
        string transaction_id
        timestamp processed_at
    }
```

### Caching Strategy

```mermaid
flowchart LR
    REQ["Incoming Request"] --> CACHE{"Redis\nCache Hit?"}
    CACHE -->|"HIT"| RESP["Return Cached\nResponse"]
    CACHE -->|"MISS"| DB[("PostgreSQL")]
    DB --> STORE["Store in Redis\nwith TTL"]
    STORE --> RESP

    subgraph TTLs["Cache TTL Config"]
        U["User profiles: 3600s"]
        P["Products: 3600s"]
        C["Categories: 7200s"]
        S["Sessions: 1800s"]
    end
```

---

## Event-Driven Architecture

### Kafka Topics & Consumers

```mermaid
flowchart LR
    subgraph Producers["Event Producers"]
        US["User Service"]
        OS["Order Service"]
        PMS["Payment Service"]
    end

    subgraph Topics["Kafka Topics"]
        T1["user-events\n• REGISTERED\n• LOGGED_IN\n• UPDATED\n• DELETED\n• PASSWORD_CHANGED"]
        T2["order-events\n• CREATED\n• CONFIRMED\n• SHIPPED\n• DELIVERED\n• CANCELLED"]
        T3["payment-events\n• INITIATED\n• COMPLETED\n• FAILED\n• REFUNDED"]
    end

    subgraph Consumers["Event Consumers"]
        NS["Notification Service\ngroup: notification-service"]
    end

    US -->|"Publish"| T1
    OS -->|"Publish"| T2
    PMS -->|"Publish"| T3

    T1 -->|"Subscribe"| NS
    T2 -->|"Subscribe"| NS
    T3 -->|"Subscribe"| NS
```

---

## Security Architecture

```mermaid
flowchart TD
    CLIENT["Client Request"] --> GW["API Gateway"]

    GW --> PUBLIC{"Public\nEndpoint?"}
    PUBLIC -->|"Yes\n/api/v1/auth/**"| US["User Service\n(no auth required)"]
    PUBLIC -->|"No"| JWT{"JWT Token\nPresent?"}

    JWT -->|"Missing"| R401["401 Unauthorized"]
    JWT -->|"Present"| VALID{"Token\nValid?"}

    VALID -->|"Expired/Invalid"| R401
    VALID -->|"Valid"| ROLE{"Role\nCheck"}

    ROLE -->|"ADMIN"| ALL["Full Access"]
    ROLE -->|"STAFF"| LIMITED["Product/Order\nManagement"]
    ROLE -->|"CUSTOMER"| OWN["Own Data Only"]

    subgraph TokenFlow["JWT Token Details"]
        AT["Access Token\nExpiry: 24h\nAlgorithm: HS384"]
        RT["Refresh Token\nExpiry: 7 days"]
    end
```

---

## Infrastructure Diagram (Local)

### Local Development Stack

```mermaid
graph TB
    subgraph Host["Developer Machine"]
        subgraph JVM["JVM Processes (mvn spring-boot:run)"]
            SD["service-discovery :8761"]
            GW["api-gateway :8080"]
            US["user-service :8081"]
            PS["product-service :8082"]
            OS["order-service :8083"]
            PMS["payment-service :8084"]
            NS["notification-service :8085"]
        end

        subgraph Node["Node Process"]
            FE["toyshop-frontend :5173\nVite + React"]
        end

        subgraph Docker["Docker Containers (infrastructure-only.yml)"]
            PG["postgres:15-alpine\ntoyshop-postgres :5432\n4 databases"]
            RD["redis:7-alpine\ntoyshop-redis :6379"]
            KF["cp-kafka:7.5.0\ntoyshop-kafka :9092"]
            ZK["cp-zookeeper:7.5.0\ntoyshop-zookeeper :2181"]
        end
    end

    GW --> SD
    US --> SD
    PS --> SD
    OS --> SD
    PMS --> SD

    US --> PG
    PS --> PG
    OS --> PG
    PMS --> PG
    US --> RD
    PS --> RD
    US --> KF
    OS --> KF
    PMS --> KF
    KF --> NS
    ZK --> KF

    FE -->|":8080"| GW
```

---

## Infrastructure Diagram (Cloud / Production)

### AWS Production Architecture

```mermaid
graph TB
    subgraph Internet["Internet"]
        USERS["End Users"]
    end

    subgraph AWS["AWS Cloud (us-east-1)"]
        subgraph PublicSubnets["Public Subnets (3 AZs)"]
            ALB["Application\nLoad Balancer"]
            NAT["NAT Gateway"]
        end

        subgraph PrivateSubnets["Private Subnets (3 AZs)"]
            subgraph EKS["EKS Cluster (Kubernetes 1.29)"]
                subgraph GeneralNG["Node Group: general (t3.medium x3)"]
                    GW_K["api-gateway\nPod x2"]
                    SD_K["service-discovery\nPod x1"]
                end
                subgraph WorkerNG["Node Group: workers (t3.large x2)"]
                    US_K["user-service\nPod x2"]
                    PS_K["product-service\nPod x2"]
                    OS_K["order-service\nPod x2"]
                    PMS_K["payment-service\nPod x2"]
                    NS_K["notification-service\nPod x1"]
                end
            end

            RDS["RDS PostgreSQL 15\ndb.t3.medium\nMulti-AZ"]
            REDIS["ElastiCache Redis 7\ncache.t3.medium"]
            MSK["MSK Kafka 3.5\nkafka.m5.large x3"]
        end

        subgraph Storage["Storage"]
            S3["S3 Bucket\ntoyshop-product-images\nVersioned + Encrypted"]
        end

        subgraph Observability["Observability"]
            CW["CloudWatch\nLogs + Metrics"]
        end

        TF["Terraform State\nS3 Backend"]
    end

    USERS -->|"HTTPS"| ALB
    ALB --> GW_K
    GW_K --> US_K
    GW_K --> PS_K
    GW_K --> OS_K
    GW_K --> PMS_K
    US_K --> RDS
    PS_K --> RDS
    OS_K --> RDS
    PMS_K --> RDS
    US_K --> REDIS
    PS_K --> REDIS
    PS_K --> S3
    US_K --> MSK
    OS_K --> MSK
    PMS_K --> MSK
    MSK --> NS_K
    EKS --> CW
    MSK --> CW
```

---

## Deployment Architecture (Kubernetes)

### K8s Resource Layout

```mermaid
graph TB
    subgraph NS_TOY["Namespace: toyshop"]
        subgraph Deployments["Deployments"]
            D1["user-service\nreplicas: 2\nimage: toyshop/user-service:latest"]
            D2["product-service\nreplicas: 2"]
            D3["order-service\nreplicas: 2"]
            D4["payment-service\nreplicas: 2"]
            D5["notification-service\nreplicas: 1"]
            D6["api-gateway\nreplicas: 2"]
            D7["service-discovery\nreplicas: 1"]
        end

        subgraph Services_K8s["Services (ClusterIP)"]
            SVC1["user-service :80 → 8081"]
            SVC2["product-service :80 → 8082"]
            SVC3["order-service :80 → 8083"]
            SVC4["payment-service :80 → 8084"]
        end

        subgraph Config["Config & Secrets"]
            CM["ConfigMap: toyshop-config\ndatabase-host, redis-host"]
            SEC["Secret: toyshop-secrets\ndatabase-password, jwt-secret"]
        end

        subgraph Probes["Health Probes (per pod)"]
            LP["Liveness: /actuator/health/liveness\ninitialDelay: 60s"]
            RP["Readiness: /actuator/health/readiness\ninitialDelay: 30s"]
        end
    end

    subgraph Resources["Resource Limits (per pod)"]
        RES["requests: 256Mi / 250m CPU\nlimits:   512Mi / 500m CPU"]
    end

    D1 --> SVC1
    D2 --> SVC2
    D3 --> SVC3
    D4 --> SVC4
    D1 --> CM
    D1 --> SEC
```

---

## Technology Stack

```mermaid
mindmap
  root((ToyShop))
    Frontend
      React 18
      Vite
      TypeScript
      Axios
    API Gateway
      Spring Cloud Gateway
      Netty Reactive
      Spring Security WebFlux
      Netflix Eureka Client
      Spring Cloud LoadBalancer
    Microservices
      Spring Boot 3.2
      Spring Data JPA
      Spring Security
      Spring Kafka
      Lombok
      JWT (jjwt 0.12)
    Databases
      PostgreSQL 15
      Redis 7
      Apache Kafka 3.5
      Zookeeper
    Service Mesh
      Netflix Eureka
      Spring Cloud LoadBalancer
      Resilience4j
    Observability
      Spring Actuator
      Micrometer
      Prometheus
      OpenTelemetry
    Infrastructure
      Docker Compose
      Kubernetes 1.29
      Terraform
      AWS EKS
      AWS RDS
      AWS ElastiCache
      AWS MSK
      AWS S3
    Testing
      JUnit 5
      Mockito
      Testcontainers
      Spring Security Test
      Jest
      React Testing Library
```

---

## Port Reference

| Service | Port | Protocol | Notes |
|---------|------|----------|-------|
| API Gateway | 8080 | HTTP/Reactive | Main entry point for all clients |
| User Service | 8081 | HTTP/Servlet | Auth, JWT issuance |
| Product Service | 8082 | HTTP/Servlet | Catalog, S3 image upload |
| Order Service | 8083 | HTTP/Servlet | Order lifecycle |
| Payment Service | 8084 | HTTP/Servlet | Transactions |
| Notification Service | 8085 | HTTP/Servlet | Email via SMTP |
| Eureka Dashboard | 8761 | HTTP | Service registry UI |
| PostgreSQL | 5432 | TCP | 4 separate databases |
| Redis | 6379 | TCP | Cache + sessions |
| Kafka | 9092 | TCP | Event streaming |
| Zookeeper | 2181 | TCP | Kafka coordination |
| Frontend (dev) | 5173 | HTTP | Vite dev server |
