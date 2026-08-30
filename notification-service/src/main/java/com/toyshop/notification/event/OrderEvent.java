package com.toyshop.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String status;
    private String eventType;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp;
}
