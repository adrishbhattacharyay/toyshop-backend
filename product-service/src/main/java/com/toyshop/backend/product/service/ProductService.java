package com.toyshop.backend.product.service;

import com.toyshop.backend.product.dto.ProductRequest;
import com.toyshop.backend.product.dto.ProductResponse;
import com.toyshop.backend.product.entity.Product;
import com.toyshop.backend.product.exception.ProductNotFoundException;
import com.toyshop.backend.product.exception.SkuAlreadyExistsException;
import com.toyshop.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service class for product-related business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Value("${app.cache.product-ttl:1800}")
    private long productCacheTtl;

    @Value("${app.cache.category-ttl:3600}")
    private long categoryCacheTtl;

    @Value("${app.cache.page-ttl:300}")
    private long pageCacheTtl;

    /**
     * Create a new product.
     */
    @CacheEvict(value = "productPages", allEntries = true)
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating new product with SKU: {}", request.getSku());

        if (productRepository.existsBySku(request.getSku())) {
            throw new SkuAlreadyExistsException(request.getSku());
        }

        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .costPrice(request.getCostPrice())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 10)
                .category(request.getCategory())
                .brand(request.getBrand())
                .ageRange(request.getAgeRange())
                .imageUrl(request.getImageUrl())
                .weightKg(request.getWeightKg())
                .dimensions(request.getDimensions())
                .featured(request.getFeatured() != null ? request.getFeatured() : false)
                .status(Product.ProductStatus.ACTIVE)
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with ID: {}", savedProduct.getId());

        return ProductResponse.fromEntity(savedProduct);
    }

    /**
     * Get product by ID.
     */
    @Cacheable(value = "products", key = "#productId")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID productId) {
        log.debug("Fetching product by ID: {}", productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductResponse.fromEntity(product);
    }

    /**
     * Get product by SKU.
     */
    @Cacheable(value = "products", key = "#sku")
    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        log.debug("Fetching product by SKU: {}", sku);
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException("sku", sku));
        return ProductResponse.fromEntity(product);
    }

    /**
     * Get all products with pagination.
     */
    @Cacheable(value = "productPages", key = "{#pageable.pageNumber, #pageable.pageSize, #pageable.sort}")
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        log.debug("Fetching all products with pagination");
        return productRepository.findAll(pageable)
                .map(ProductResponse::fromEntity);
    }

    /**
     * Get products by category.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(String category, Pageable pageable) {
        log.debug("Fetching products by category: {}", category);
        return productRepository.findByCategory(category, pageable)
                .map(ProductResponse::fromEntity);
    }

    /**
     * Search products by name.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String query, Pageable pageable) {
        log.debug("Searching products with query: {}", query);
        return productRepository.findByNameContainingIgnoreCase(query, pageable)
                .map(ProductResponse::fromEntity);
    }

    /**
     * Get featured products.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getFeaturedProducts(Pageable pageable) {
        log.debug("Fetching featured products");
        return productRepository.findByFeaturedTrue(pageable)
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get low stock products.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        log.debug("Fetching low stock products");
        return productRepository.findLowStockProducts()
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get out of stock products.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getOutOfStockProducts() {
        log.debug("Fetching out of stock products");
        return productRepository.findOutOfStockProducts()
                .stream()
                .map(ProductResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get all categories.
     */
    @Cacheable(value = "categories", key = "'all'")
    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        log.debug("Fetching all categories");
        return productRepository.findAllCategories();
    }

    /**
     * Update product.
     */
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#productId"),
        @CacheEvict(value = "productPages", allEntries = true)
    })
    @Transactional
    public ProductResponse updateProduct(UUID productId, ProductRequest request) {
        log.info("Updating product: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new SkuAlreadyExistsException(request.getSku());
        }

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCostPrice(request.getCostPrice());
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        if (request.getLowStockThreshold() != null) {
            product.setLowStockThreshold(request.getLowStockThreshold());
        }
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setAgeRange(request.getAgeRange());
        product.setImageUrl(request.getImageUrl());
        product.setWeightKg(request.getWeightKg());
        product.setDimensions(request.getDimensions());
        if (request.getFeatured() != null) {
            product.setFeatured(request.getFeatured());
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully: {}", productId);

        return ProductResponse.fromEntity(updatedProduct);
    }

    /**
     * Update product stock.
     */
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#productId"),
        @CacheEvict(value = "productPages", allEntries = true)
    })
    @Transactional
    public ProductResponse updateStock(UUID productId, int quantityChange) {
        log.info("Updating stock for product {} by {}", productId, quantityChange);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        int newQuantity = product.getStockQuantity() + quantityChange;
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock cannot go below zero");
        }

        product.setStockQuantity(newQuantity);

        if (newQuantity == 0) {
            product.setStatus(Product.ProductStatus.OUT_OF_STOCK);
        } else if (product.getStatus() == Product.ProductStatus.OUT_OF_STOCK) {
            product.setStatus(Product.ProductStatus.ACTIVE);
        }

        Product updatedProduct = productRepository.save(product);
        return ProductResponse.fromEntity(updatedProduct);
    }

    /**
     * Delete product.
     */
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#productId"),
        @CacheEvict(value = "productPages", allEntries = true)
    })
    @Transactional
    public void deleteProduct(UUID productId) {
        log.info("Deleting product: {}", productId);

        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }

        productRepository.deleteById(productId);
        log.info("Product deleted successfully: {}", productId);
    }

    /**
     * Get product entity by ID (for internal use).
     */
    @Transactional(readOnly = true)
    public Product getProductEntityById(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}