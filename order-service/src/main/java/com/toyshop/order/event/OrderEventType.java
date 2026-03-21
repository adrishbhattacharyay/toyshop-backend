package com.toyshop.order.event;

public enum OrderEventType {
    CREATED,
    STATUS_CHANGED,
    PAYMENT_RECEIVED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}