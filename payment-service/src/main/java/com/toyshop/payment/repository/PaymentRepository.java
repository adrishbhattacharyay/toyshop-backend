package com.toyshop.payment.repository;

import com.toyshop.payment.entity.Payment;
import com.toyshop.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByUserId(Long userId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    boolean existsByOrderId(Long orderId);
}