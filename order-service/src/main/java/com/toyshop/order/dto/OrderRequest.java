package com.toyshop.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Size(max = 500, message = "Shipping address must not exceed 500 characters")
    private String shippingAddress;

    @Size(max = 500, message = "Billing address must not exceed 500 characters")
    private String billingAddress;

    @Size(max = 50, message = "Shipping method must not exceed 50 characters")
    private String shippingMethod;

    @Size(max = 50, message = "Payment method must not exceed 50 characters")
    private String paymentMethod;

    @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
    private BigDecimal discountAmount;

    @Size(max = 1000, message = "Customer notes must not exceed 1000 characters")
    private String customerNotes;
}