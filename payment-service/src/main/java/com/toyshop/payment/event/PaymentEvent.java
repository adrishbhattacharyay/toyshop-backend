package com.toyshop.payment.event;

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
public class PaymentEvent {
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private String transactionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private PaymentEventType eventType;
    private LocalDateTime timestamp;
}