package com.toyshop.backend.product.service;

import com.toyshop.backend.product.dto.ProductRequest;
import com.toyshop.backend.product.dto.ProductResponse;
import com.toyshop.backend.product.entity.Product;
import com.toyshop.backend.product.exception.ProductNotFoundException;
import com.toyshop.backend.product.exception.SkuAlreadyExistsException;
import com.toyshop.backend.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private ProductRequest productRequest;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        
        testProduct = Product.builder()
                .id(productId)
                .sku("TOY-001")
                .name("Action Figure")
                .description("A cool action figure")
                .price(new BigDecimal("29.99"))
                .costPrice(new BigDecimal("15.00"))
                .stockQuantity(100)
                .lowStockThreshold(10)
                .category("Action Figures")
                .brand("ToyCo")
                .ageRange("8+")
                .status(Product.ProductStatus.ACTIVE)
                .featured(false)
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        productRequest = ProductRequest.builder()
                .sku("TOY-002")
                .name("New Action Figure")
                .description("A new action figure")
                .price(new BigDecimal("39.99"))
                .stockQuantity(50)
                .category("Action Figures")
                .brand("ToyCo")
                .build();
    }

    @Nested
    @DisplayName("Create Product Tests")
    class CreateProductTests {

        @Test
        @DisplayName("Should create product successfully")
        void shouldCreateProductSuccessfully() {
            when(productRepository.existsBySku(productRequest.getSku())).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product product = invocation.getArgument(0);
                product.setId(UUID.randomUUID());
                product.setCreatedAt(LocalDateTime.now());
                return product;
            });

            ProductResponse response = productService.createProduct(productRequest);

            assertThat(response).isNotNull();
            assertThat(response.getSku()).isEqualTo(productRequest.getSku());
            assertThat(response.getName()).isEqualTo(productRequest.getName());
            assertThat(response.getPrice()).isEqualTo(productRequest.getPrice());
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw exception when SKU exists")
        void shouldThrowExceptionWhenSkuExists() {
            when(productRepository.existsBySku(productRequest.getSku())).thenReturn(true);

            assertThatThrownBy(() -> productService.createProduct(productRequest))
                    .isInstanceOf(SkuAlreadyExistsException.class)
                    .hasMessageContaining(productRequest.getSku());
        }
    }

    @Nested
    @DisplayName("Get Product Tests")
    class GetProductTests {

        @Test
        @DisplayName("Should get product by ID")
        void shouldGetProductById() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            ProductResponse response = productService.getProductById(productId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(productId);
            assertThat(response.getSku()).isEqualTo(testProduct.getSku());
        }

        @Test
        @DisplayName("Should throw exception when product not found")
        void shouldThrowExceptionWhenProductNotFound() {
            UUID nonExistentId = UUID.randomUUID();
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(nonExistentId))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("Should get product by SKU")
        void shouldGetProductBySku() {
            when(productRepository.findBySku(testProduct.getSku())).thenReturn(Optional.of(testProduct));

            ProductResponse response = productService.getProductBySku(testProduct.getSku());

            assertThat(response).isNotNull();
            assertThat(response.getSku()).isEqualTo(testProduct.getSku());
        }
    }

    @Nested
    @DisplayName("Update Product Tests")
    class UpdateProductTests {

        @Test
        @DisplayName("Should update product successfully")
        void shouldUpdateProductSuccessfully() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductRequest updateRequest = ProductRequest.builder()
                    .sku(testProduct.getSku())
                    .name("Updated Name")
                    .price(new BigDecimal("49.99"))
                    .stockQuantity(75)
                    .build();

            ProductResponse response = productService.updateProduct(productId, updateRequest);

            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Updated Name");
            assertThat(response.getPrice()).isEqualTo(new BigDecimal("49.99"));
            assertThat(response.getStockQuantity()).isEqualTo(75);
        }
    }

    @Nested
    @DisplayName("Update Stock Tests")
    class UpdateStockTests {

        @Test
        @DisplayName("Should increase stock")
        void shouldIncreaseStock() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductResponse response = productService.updateStock(productId, 50);

            assertThat(response.getStockQuantity()).isEqualTo(150);
        }

        @Test
        @DisplayName("Should decrease stock")
        void shouldDecreaseStock() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductResponse response = productService.updateStock(productId, -20);

            assertThat(response.getStockQuantity()).isEqualTo(80);
        }

        @Test
        @DisplayName("Should set status to OUT_OF_STOCK when quantity reaches zero")
        void shouldSetStatusToOutOfStockWhenZero() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductResponse response = productService.updateStock(productId, -100);

            assertThat(response.getStockQuantity()).isEqualTo(0);
            assertThat(response.getStatus()).isEqualTo(Product.ProductStatus.OUT_OF_STOCK);
        }
    }

    @Nested
    @DisplayName("Delete Product Tests")
    class DeleteProductTests {

        @Test
        @DisplayName("Should delete product successfully")
        void shouldDeleteProductSuccessfully() {
            when(productRepository.existsById(productId)).thenReturn(true);
            doNothing().when(productRepository).deleteById(productId);

            assertThatCode(() -> productService.deleteProduct(productId))
                    .doesNotThrowAnyException();

            verify(productRepository).deleteById(productId);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent product")
        void shouldThrowExceptionWhenDeletingNonExistentProduct() {
            UUID nonExistentId = UUID.randomUUID();
            when(productRepository.existsById(nonExistentId)).thenReturn(false);

            assertThatThrownBy(() -> productService.deleteProduct(nonExistentId))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }
}