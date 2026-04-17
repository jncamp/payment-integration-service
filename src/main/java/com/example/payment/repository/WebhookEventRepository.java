package com.example.payment.repository;

import com.example.payment.entity.WebhookEventEntity;
import com.example.payment.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, UUID> {
    Optional<WebhookEventEntity> findByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);
}
