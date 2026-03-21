package com.toyshop.order.service;

import com.toyshop.order.dto.*;
import com.toyshop.order.entity.*;
import com.toyshop.order.event.OrderEvent;
import com.toyshop.order.event.OrderEventType;
import com.toyshop.order.exception.OrderNotFoundException;
import com.toyshop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private static final String ORDER_EVENTS_TOPIC = "order-events";

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating new order for user: {}", request.getUserId());

        String orderNumber = generateOrderNumber();
        BigDecimal subtotal = calculateSubtotal(request.getItems());
        BigDecimal taxAmount = subtotal.multiply(new BigDecimal("0.18")); // 18% tax
        BigDecimal shippingAmount = calculateShipping(subtotal);
        BigDecimal totalAmount = subtotal.add(taxAmount).add(shippingAmount);

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(request.getUserId())
                .status(OrderStatus.PENDING)
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .shippingAmount(shippingAmount)
                .discountAmount(request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .shippingAddress(request.getShippingAddress())
                .billingAddress(request.getBillingAddress())
                .shippingMethod(request.getShippingMethod())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .customerNotes(request.getCustomerNotes())
                .build();

        // Add order items
        for (OrderItemRequest itemRequest : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .productSku(itemRequest.getProductSku())
                    .productName(itemRequest.getProductName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(itemRequest.getUnitPrice())
                    .discountAmount(itemRequest.getDiscountAmount() != null ? itemRequest.getDiscountAmount() : BigDecimal.ZERO)
                    .build();
            item.calculateTotalPrice();
            order.addItem(item);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with number: {}", savedOrder.getOrderNumber());

        publishOrderEvent(savedOrder, OrderEventType.CREATED);

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus status) {
        log.info("Updating order status for ID: {} to: {}", id, status);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));

        order.setStatus(status);

        switch (status) {
            case CONFIRMED:
                order.setConfirmedAt(LocalDateTime.now());
                break;
            case SHIPPED:
                order.setShippedAt(LocalDateTime.now());
                break;
            case DELIVERED:
                order.setDeliveredAt(LocalDateTime.now());
                break;
            case CANCELLED:
                order.setCancelledAt(LocalDateTime.now());
                break;
            default:
                break;
        }

        Order updatedOrder = orderRepository.save(order);
        publishOrderEvent(updatedOrder, OrderEventType.STATUS_CHANGED);

        return OrderResponse.from(updatedOrder);
    }

    @Transactional
    public OrderResponse getOrderById(Long id) {
        log.debug("Fetching order by ID: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        log.debug("Fetching order by number: {}", orderNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with number: " + orderNumber));

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        log.debug("Fetching orders for user: {}", userId);

        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        log.debug("Fetching orders by status: {}", status);

        return orderRepository.findByStatus(status).stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        log.debug("Fetching all orders");

        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        log.info("Cancelling order with ID: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel order that has been shipped or delivered");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        Order cancelledOrder = orderRepository.save(order);
        publishOrderEvent(cancelledOrder, OrderEventType.CANCELLED);

        return OrderResponse.from(cancelledOrder);
    }

    @Transactional
    public OrderResponse updatePaymentStatus(Long id, PaymentStatus paymentStatus, String transactionId) {
        log.info("Updating payment status for order ID: {} to: {}", id, paymentStatus);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));

        order.setPaymentStatus(paymentStatus);
        order.setPaymentTransactionId(transactionId);

        if (paymentStatus == PaymentStatus.COMPLETED) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setConfirmedAt(LocalDateTime.now());
        }

        Order updatedOrder = orderRepository.save(order);
        publishOrderEvent(updatedOrder, OrderEventType.PAYMENT_RECEIVED);

        return OrderResponse.from(updatedOrder);
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private BigDecimal calculateSubtotal(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                        .subtract(item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateShipping(BigDecimal subtotal) {
        if (subtotal.compareTo(new BigDecimal("500")) >= 0) {
            return BigDecimal.ZERO; // Free shipping for orders over 500
        }
        return new BigDecimal("50"); // Flat shipping rate
    }

    private void publishOrderEvent(Order order, OrderEventType eventType) {
        OrderEvent event = OrderEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .build();
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, event);
    }
}