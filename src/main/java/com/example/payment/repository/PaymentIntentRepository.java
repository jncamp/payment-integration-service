package com.example.payment.repository;

import com.example.payment.entity.ApiClient;
import com.example.payment.entity.PaymentIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID> {
    Optional<PaymentIntentEntity> findByProviderPaymentIntentId(String providerPaymentIntentId);
    Optional<PaymentIntentEntity> findByClientAndIdempotencyKey(ApiClient client, String idempotencyKey);
    Optional<PaymentIntentEntity> findByClientSecret(String clientSecret);
}
