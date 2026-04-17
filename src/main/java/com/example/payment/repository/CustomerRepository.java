package com.example.payment.repository;

import com.example.payment.entity.ApiClient;
import com.example.payment.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByClientAndEmailIgnoreCase(ApiClient client, String email);
}
