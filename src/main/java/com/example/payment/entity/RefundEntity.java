package com.example.payment.entity;

import com.example.payment.enums.RefundStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class RefundEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_intent_id", nullable = false)
    private PaymentIntentEntity paymentIntent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private ChargeEntity charge;

    @Column(name = "internal_reference", nullable = false, unique = true, length = 50)
    private String internalReference;

    @Column(name = "provider_refund_id", unique = true, length = 120)
    private String providerRefundId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RefundStatus status;

    @Column(length = 80)
    private String reason;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "succeeded_at")
    private OffsetDateTime succeededAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PaymentIntentEntity getPaymentIntent() { return paymentIntent; }
    public void setPaymentIntent(PaymentIntentEntity paymentIntent) { this.paymentIntent = paymentIntent; }
    public ChargeEntity getCharge() { return charge; }
    public void setCharge(ChargeEntity charge) { this.charge = charge; }
    public String getInternalReference() { return internalReference; }
    public void setInternalReference(String internalReference) { this.internalReference = internalReference; }
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) { this.providerRefundId = providerRefundId; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getSucceededAt() { return succeededAt; }
    public void setSucceededAt(OffsetDateTime succeededAt) { this.succeededAt = succeededAt; }
}
