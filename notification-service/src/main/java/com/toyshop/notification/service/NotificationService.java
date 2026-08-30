package com.toyshop.notification.service;

import com.toyshop.notification.dto.NotificationRequest;
import com.toyshop.notification.dto.NotificationResponse;
import com.toyshop.notification.event.OrderEvent;
import com.toyshop.notification.event.PaymentEvent;
import com.toyshop.notification.event.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    // ── Kafka Consumers ──────────────────────────────────────────────────────

    @KafkaListener(topics = "user-events", groupId = "notification-service",
                   containerFactory = "userEventListenerFactory")
    public void handleUserEvent(UserEvent event) {
        log.info("USER EVENT: type={} user={} email={}", event.getEventType(), event.getUsername(), event.getEmail());
        if (event.getEmail() == null) return;

        switch (event.getEventType() != null ? event.getEventType() : "") {
            case "REGISTERED" -> sendEmail(event.getEmail(),
                "Welcome to VolcanoToysForKids! 🌋",
                String.format("Hi %s,\n\nWelcome to VolcanoToysForKids! Your account has been created.\n\nHappy Shopping!", event.getUsername()));
            case "PASSWORD_CHANGED" -> sendEmail(event.getEmail(),
                "Your password was changed",
                String.format("Hi %s,\n\nYour password was recently changed. If this wasn't you, contact support immediately.", event.getUsername()));
            default -> log.debug("No email action for user event type: {}", event.getEventType());
        }
    }

    @KafkaListener(topics = "order-events", groupId = "notification-service",
                   containerFactory = "orderEventListenerFactory")
    public void handleOrderEvent(OrderEvent event) {
        log.info("ORDER EVENT: type={} order={} total={}", event.getEventType(), event.getOrderNumber(), event.getTotalAmount());
        // We don't have the user's email in the order event - log for now
        // In production, order service would include email or we'd look it up
        switch (event.getEventType() != null ? event.getEventType() : "") {
            case "CREATED"   -> log.info("Order {} created - total ₹{}", event.getOrderNumber(), event.getTotalAmount());
            case "CONFIRMED" -> log.info("Order {} confirmed", event.getOrderNumber());
            case "SHIPPED"   -> log.info("Order {} shipped", event.getOrderNumber());
            case "CANCELLED" -> log.info("Order {} cancelled", event.getOrderNumber());
            default          -> log.debug("Order event: {}", event.getEventType());
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-service",
                   containerFactory = "paymentEventListenerFactory")
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("PAYMENT EVENT: type={} order={} amount={}", event.getEventType(), event.getOrderNumber(), event.getAmount());
        // Same - no email in payment event, log for now
        switch (event.getEventType() != null ? event.getEventType() : "") {
            case "COMPLETED" -> log.info("Payment completed for order {} - txn {}", event.getOrderNumber(), event.getTransactionId());
            case "FAILED"    -> log.warn("Payment FAILED for order {}", event.getOrderNumber());
            case "REFUNDED"  -> log.info("Payment refunded for order {}", event.getOrderNumber());
            default          -> log.debug("Payment event: {}", event.getEventType());
        }
    }

    // ── Email Sending ────────────────────────────────────────────────────────

    public void sendEmail(String to, String subject, String body) {
        try {
            log.info("Sending email to={} subject={}", to, subject);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@volcanoToys.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.warn("Failed to send email to {} - {} (is MailHog running on :1025?)", to, e.getMessage());
        }
    }

    public NotificationResponse sendEmail(NotificationRequest request) {
        sendEmail(request.getTo(), request.getSubject(), request.getBody());
        return NotificationResponse.builder()
                .success(true)
                .message("Email sent")
                .recipient(request.getTo())
                .type("EMAIL")
                .build();
    }
}
