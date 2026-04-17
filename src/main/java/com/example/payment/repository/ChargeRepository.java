package com.example.payment.repository;

import com.example.payment.entity.ChargeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository extends JpaRepository<ChargeEntity, UUID> {
    Optional<ChargeEntity> findByProviderChargeId(String providerChargeId);
}
