package com.example.payment.repository;

import com.example.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByProviderPaymentId(String providerPaymentId);
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
}
