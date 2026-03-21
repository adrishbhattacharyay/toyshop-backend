package com.toyshop.payment.dto;

import com.toyshop.payment.entity.Payment;
import com.toyshop.payment.entity.PaymentMethod;
import com.toyshop.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private Long userId;
    private String transactionId;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String currency;
    private String errorCode;
    private String errorMessage;
    private String refundId;
    private BigDecimal refundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .transactionId(payment.getTransactionId())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .errorCode(payment.getErrorCode())
                .errorMessage(payment.getErrorMessage())
                .refundId(payment.getRefundId())
                .refundAmount(payment.getRefundAmount())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}