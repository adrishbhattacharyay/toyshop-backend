package com.toyshop.payment.service;

import com.toyshop.payment.dto.*;
import com.toyshop.payment.entity.*;
import com.toyshop.payment.event.PaymentEvent;
import com.toyshop.payment.event.PaymentEventType;
import com.toyshop.payment.exception.PaymentException;
import com.toyshop.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for order: {}", request.getOrderId());

        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            throw new PaymentException("Payment already exists for order: " + request.getOrderId());
        }

        String transactionId = generateTransactionId();
        
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .transactionId(transactionId)
                .status(PaymentStatus.PROCESSING)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .build();

        // Simulate payment processing
        PaymentResult result = simulatePaymentProcessing(request);
        
        if (result.isSuccess()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(LocalDateTime.now());
            payment.setGatewayResponse(result.getResponse());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorCode(result.getErrorCode());
            payment.setErrorMessage(result.getErrorMessage());
            payment.setGatewayResponse(result.getResponse());
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment processed: {} with status: {}", transactionId, savedPayment.getStatus());

        publishPaymentEvent(savedPayment, 
            savedPayment.getStatus() == PaymentStatus.COMPLETED ? 
            PaymentEventType.PAYMENT_COMPLETED : PaymentEventType.PAYMENT_FAILED);

        return PaymentResponse.from(savedPayment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount) {
        log.info("Processing refund for payment: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException("Cannot refund payment with status: " + payment.getStatus());
        }

        BigDecimal refundAmount = amount != null ? amount : payment.getAmount();
        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new PaymentException("Refund amount exceeds payment amount");
        }

        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 8);
        
        if (refundAmount.compareTo(payment.getAmount()) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        
        payment.setRefundId(refundId);
        payment.setRefundAmount(refundAmount);

        Payment refundedPayment = paymentRepository.save(payment);
        log.info("Payment refunded: {} with amount: {}", refundId, refundAmount);

        publishPaymentEvent(refundedPayment, PaymentEventType.PAYMENT_REFUNDED);

        return PaymentResponse.from(refundedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        log.debug("Fetching payment by ID: {}", id);
        return PaymentResponse.from(paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException("Payment not found: " + id)));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        log.debug("Fetching payment by transaction ID: {}", transactionId);
        return PaymentResponse.from(paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + transactionId)));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        log.debug("Fetching payment by order ID: {}", orderId);
        return PaymentResponse.from(paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentException("Payment not found for order: " + orderId)));
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private PaymentResult simulatePaymentProcessing(PaymentRequest request) {
        // Simulate payment gateway response
        try {
            Thread.sleep(100); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 95% success rate
        if (Math.random() < 0.95) {
            return PaymentResult.success("Payment processed successfully at " + LocalDateTime.now());
        } else {
            return PaymentResult.failure("GATEWAY_ERROR", "Payment gateway temporarily unavailable", "Gateway timeout");
        }
    }

    private void publishPaymentEvent(Payment payment, PaymentEventType eventType) {
        PaymentEvent event = PaymentEvent.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .transactionId(payment.getTransactionId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .build();
        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event);
    }

    private static class PaymentResult {
        private final boolean success;
        private final String response;
        private final String errorCode;
        private final String errorMessage;

        private PaymentResult(boolean success, String response, String errorCode, String errorMessage) {
            this.success = success;
            this.response = response;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static PaymentResult success(String response) {
            return new PaymentResult(true, response, null, null);
        }

        public static PaymentResult failure(String errorCode, String errorMessage, String response) {
            return new PaymentResult(false, response, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getResponse() { return response; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
}