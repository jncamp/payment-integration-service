package com.example.payment.repository;

import com.example.payment.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {
    Optional<ApiClient> findByClientCode(String clientCode);
}
