# Kubernetes Manifests for ToyShop Services

# Namespace
apiVersion: v1
kind: Namespace
metadata:
  name: toyshop
  labels:
    app: toyshop

---
# ConfigMap for shared configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: toyshop-config
  namespace: toyshop
data:
  SPRING_PROFILES_ACTIVE: "kubernetes"
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: "http://eureka-service:8761/eureka/"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://toyshop-postgres:5432/toyshop"
  SPRING_REDIS_HOST: "toyshop-redis"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "toyshop-kafka:9092"

---
# User Service Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: toyshop
  labels:
    app: user-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
      - name: user-service
        image: toyshop/user-service:latest
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service:8761/eureka/"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5

---
# User Service Service
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: toyshop
spec:
  selector:
    app: user-service
  ports:
  - port: 8081
    targetPort: 8081
  type: ClusterIP

---
# Product Service Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product-service
  namespace: toyshop
  labels:
    app: product-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: product-service
  template:
    metadata:
      labels:
        app: product-service
    spec:
      containers:
      - name: product-service
        image: toyshop/product-service:latest
        ports:
        - containerPort: 8082
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service:8761/eureka/"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"

---
# Product Service Service
apiVersion: v1
kind: Service
metadata:
  name: product-service
  namespace: toyshop
spec:
  selector:
    app: product-service
  ports:
  - port: 8082
    targetPort: 8082
  type: ClusterIP

---
# Order Service Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: toyshop
  labels:
    app: order-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: toyshop/order-service:latest
        ports:
        - containerPort: 8083
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service:8761/eureka/"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"

---
# Order Service Service
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: toyshop
spec:
  selector:
    app: order-service
  ports:
  - port: 8083
    targetPort: 8083
  type: ClusterIP

---
# Payment Service Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: toyshop
  labels:
    app: payment-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
    spec:
      containers:
      - name: payment-service
        image: toyshop/payment-service:latest
        ports:
        - containerPort: 8084
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service:8761/eureka/"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"

---
# Payment Service Service
apiVersion: v1
kind: Service
metadata:
  name: payment-service
  namespace: toyshop
spec:
  selector:
    app: payment-service
  ports:
  - port: 8084
    targetPort: 8084
  type: ClusterIP

---
# Notification Service Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notification-service
  namespace: toyshop
  labels:
    app: notification-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: notification-service
  template:
    metadata:
      labels:
        app: notification-service
    spec:
      containers:
      - name: notification-service
        image: toyshop/notification-service:latest
        ports:
        - containerPort: 8085
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service:8761/eureka/"
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "200m"

---
# Notification Service Service
apiVersion: v1
kind: Service
metadata:
  name: notification-service
  namespace: toyshop
spec:
  selector:
    app: notification-service
  ports:
  - port: 8085
    targetPort: 8085
  type: ClusterIP

---
# API Gateway Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: toyshop
  labels:
    app: api-gateway
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
      - name: api-gateway
        image: toyshop/api-gateway:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
          value: "http://eureka-service:8761/eureka/"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"

---
# API Gateway Service (LoadBalancer for external access)
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: toyshop
spec:
  selector:
    app: api-gateway
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer

---
# Eureka Service Discovery
apiVersion: apps/v1
kind: Deployment
metadata:
  name: eureka-service
  namespace: toyshop
  labels:
    app: eureka-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: eureka-service
  template:
    metadata:
      labels:
        app: eureka-service
    spec:
      containers:
      - name: eureka-service
        image: toyshop/service-discovery:latest
        ports:
        - containerPort: 8761
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "200m"

---
# Eureka Service
apiVersion: v1
kind: Service
metadata:
  name: eureka-service
  namespace: toyshop
spec:
  selector:
    app: eureka-service
  ports:
  - port: 8761
    targetPort: 8761
  type: ClusterIP

---
# Horizontal Pod Autoscaler for User Service
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: user-service-hpa
  namespace: toyshop
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: user-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80