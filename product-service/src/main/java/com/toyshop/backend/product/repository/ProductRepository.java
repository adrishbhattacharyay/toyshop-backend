package com.toyshop.backend.product.repository;

import com.toyshop.backend.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Product entity operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Product> findByStatus(Product.ProductStatus status);

    List<Product> findByCategory(String category);

    Page<Product> findByCategory(String category, Pageable pageable);

    List<Product> findByBrand(String brand);

    List<Product> findByNameContainingIgnoreCase(String name);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= p.lowStockThreshold AND p.status = 'ACTIVE'")
    List<Product> findLowStockProducts();

    @Query("SELECT p FROM Product p WHERE p.stockQuantity = 0 OR p.status = 'OUT_OF_STOCK'")
    List<Product> findOutOfStockProducts();

    List<Product> findByFeaturedTrue();

    Page<Product> findByFeaturedTrue(Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.status = 'ACTIVE' ORDER BY p.averageRating DESC")
    List<Product> findTopRatedByCategory(@Param("category") String category, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'ACTIVE' ORDER BY p.createdAt DESC")
    List<Product> findRecentlyAdded(Pageable pageable);

    long countByStatus(Product.ProductStatus status);

    long countByCategory(String category);
}