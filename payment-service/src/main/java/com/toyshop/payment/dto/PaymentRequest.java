package com.toyshop.payment.dto;

import com.toyshop.payment.entity.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    @Size(max = 500, message = "Card number must not exceed 500 characters")
    private String cardNumber;

    @Size(max = 10, message = "Expiry date must not exceed 10 characters")
    private String expiryDate;

    private String cvv;

    @Size(max = 50, message = "UPI ID must not exceed 50 characters")
    private String upiId;
}