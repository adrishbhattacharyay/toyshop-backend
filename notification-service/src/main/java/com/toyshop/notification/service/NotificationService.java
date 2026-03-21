package com.toyshop.notification.service;

import com.toyshop.notification.dto.NotificationRequest;
import com.toyshop.notification.dto.NotificationResponse;
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

    public NotificationResponse sendEmail(NotificationRequest request) {
        log.info("Sending email to: {}", request.getTo());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@toyshop.com");
        message.setTo(request.getTo());
        message.setSubject(request.getSubject());
        message.setText(request.getBody());

        mailSender.send(message);

        log.info("Email sent successfully to: {}", request.getTo());

        return NotificationResponse.builder()
                .success(true)
                .message("Email sent successfully")
                .recipient(request.getTo())
                .type("EMAIL")
                .build();
    }

    @KafkaListener(topics = {"user-events", "order-events", "payment-events"})
    public void handleEvent(Object event) {
        log.info("Received event: {}", event);
        // Process events and send appropriate notifications
        // This is a simplified implementation
    }

    public void sendOrderConfirmation(String email, String orderNumber, Double totalAmount) {
        NotificationRequest request = NotificationRequest.builder()
                .to(email)
                .subject("Order Confirmation - " + orderNumber)
                .body(String.format("Your order %s has been confirmed. Total: ₹%.2f", orderNumber, totalAmount))
                .type("EMAIL")
                .build();
        sendEmail(request);
    }

    public void sendOrderShipped(String email, String orderNumber, String trackingNumber) {
        NotificationRequest request = NotificationRequest.builder()
                .to(email)
                .subject("Your Order Has Been Shipped - " + orderNumber)
                .body(String.format("Your order %s has been shipped. Tracking number: %s", orderNumber, trackingNumber))
                .type("EMAIL")
                .build();
        sendEmail(request);
    }

    public void sendPaymentConfirmation(String email, String orderNumber, String transactionId) {
        NotificationRequest request = NotificationRequest.builder()
                .to(email)
                .subject("Payment Received - Order " + orderNumber)
                .body(String.format("Payment received for order %s. Transaction ID: %s", orderNumber, transactionId))
                .type("EMAIL")
                .build();
        sendEmail(request);
    }

    public void sendPasswordReset(String email, String resetLink) {
        NotificationRequest request = NotificationRequest.builder()
                .to(email)
                .subject("Password Reset Request")
                .body("Click the link to reset your password: " + resetLink)
                .type("EMAIL")
                .build();
        sendEmail(request);
    }
}