package com.toyshop.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEvent {
    private Long paymentId;
    private Long orderId;
    private String orderNumber;
    private String status;
    private String eventType;
    private BigDecimal amount;
    private String transactionId;
    private LocalDateTime timestamp;
}
