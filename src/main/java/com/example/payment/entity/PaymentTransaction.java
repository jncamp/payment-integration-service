package com.example.payment.entity;

import com.example.payment.enums.PaymentProvider;
import com.example.payment.enums.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 120)
    private String customerEmail;

    @Column(nullable = false, length = 120)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(length = 120, unique = true)
    private String providerPaymentId;

    @Column(length = 120, unique = true)
    private String clientSecret;

    @Column(length = 120)
    private String latestChargeId;

    @Column(length = 120)
    private String providerRefundId;
    @Column(name = "refunded_amount", nullable = false)
    private Long refundedAmount = 0L;

    @Column(length = 255)
    private String failureReason;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public PaymentProvider getProvider() { return provider; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) { this.providerRefundId = providerRefundId; }
    public Long getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(Long refundedAmount) { this.refundedAmount = refundedAmount; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getLatestChargeId() { return latestChargeId; }
    public void setLatestChargeId(String latestChargeId) { this.latestChargeId = latestChargeId; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
