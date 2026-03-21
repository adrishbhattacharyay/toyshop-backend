package com.toyshop.backend.product.dto;

import com.toyshop.backend.product.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for product response data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private UUID id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer stockQuantity;
    private Integer lowStockThreshold;
    private String category;
    private String brand;
    private String ageRange;
    private String imageUrl;
    private BigDecimal weightKg;
    private String dimensions;
    private Product.ProductStatus status;
    private boolean featured;
    private BigDecimal averageRating;
    private int reviewCount;
    private boolean lowStock;
    private boolean inStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse fromEntity(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .costPrice(product.getCostPrice())
                .stockQuantity(product.getStockQuantity())
                .lowStockThreshold(product.getLowStockThreshold())
                .category(product.getCategory())
                .brand(product.getBrand())
                .ageRange(product.getAgeRange())
                .imageUrl(product.getImageUrl())
                .weightKg(product.getWeightKg())
                .dimensions(product.getDimensions())
                .status(product.getStatus())
                .featured(product.isFeatured())
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .lowStock(product.isLowStock())
                .inStock(product.isInStock())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}