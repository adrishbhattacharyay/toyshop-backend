package com.toyshop.backend.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Product Service Application - Main entry point for the Product microservice.
 * Handles product catalog and inventory management.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}