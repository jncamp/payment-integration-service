package com.example.payment.repository;

import com.example.payment.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundRepository extends JpaRepository<RefundEntity, UUID> {
    java.util.Optional<RefundEntity> findTopByPaymentIntentOrderByCreatedAtDesc(com.example.payment.entity.PaymentIntentEntity paymentIntent);
}
