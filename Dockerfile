FROM eclipse-temurin:17-jdk-alpine as builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests -pl user-service,product-service,order-service,payment-service,notification-service,api-gateway,service-discovery -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built artifacts
COPY --from=builder /app/service-discovery/target/*.jar service-discovery.jar
COPY --from=builder /app/user-service/target/*.jar user-service.jar
COPY --from=builder /app/product-service/target/*.jar product-service.jar
COPY --from=builder /app/order-service/target/*.jar order-service.jar
COPY --from=builder /app/payment-service/target/*.jar payment-service.jar
COPY --from=builder /app/notification-service/target/*.jar notification-service.jar
COPY --from=builder /app/api-gateway/target/*.jar api-gateway.jar

# Expose ports
EXPOSE 8080 8081 8082 8083 8084 8085 8761

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Default command (can be overridden)
CMD ["java", "-jar", "api-gateway.jar"]