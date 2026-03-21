package com.toyshop.payment.event;

public enum PaymentEventType {
    PAYMENT_INITIATED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,
    PAYMENT_CANCELLED
}