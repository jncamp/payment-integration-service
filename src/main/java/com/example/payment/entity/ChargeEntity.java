package com.example.payment.entity;

import com.example.payment.enums.ChargeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "charges")
public class ChargeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_intent_id", nullable = false)
    private PaymentIntentEntity paymentIntent;

    @Column(name = "provider_charge_id", unique = true, length = 120)
    private String providerChargeId;

    @Column(name = "amount_authorized", nullable = false)
    private Long amountAuthorized;

    @Column(name = "amount_captured", nullable = false)
    private Long amountCaptured;

    @Column(name = "amount_refunded", nullable = false)
    private Long amountRefunded;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChargeStatus status;

    @Column(name = "payment_method_type", length = 50)
    private String paymentMethodType;

    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "authorized_at")
    private OffsetDateTime authorizedAt;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PaymentIntentEntity getPaymentIntent() { return paymentIntent; }
    public void setPaymentIntent(PaymentIntentEntity paymentIntent) { this.paymentIntent = paymentIntent; }
    public String getProviderChargeId() { return providerChargeId; }
    public void setProviderChargeId(String providerChargeId) { this.providerChargeId = providerChargeId; }
    public Long getAmountAuthorized() { return amountAuthorized; }
    public void setAmountAuthorized(Long amountAuthorized) { this.amountAuthorized = amountAuthorized; }
    public Long getAmountCaptured() { return amountCaptured; }
    public void setAmountCaptured(Long amountCaptured) { this.amountCaptured = amountCaptured; }
    public Long getAmountRefunded() { return amountRefunded; }
    public void setAmountRefunded(Long amountRefunded) { this.amountRefunded = amountRefunded; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public ChargeStatus getStatus() { return status; }
    public void setStatus(ChargeStatus status) { this.status = status; }
    public String getPaymentMethodType() { return paymentMethodType; }
    public void setPaymentMethodType(String paymentMethodType) { this.paymentMethodType = paymentMethodType; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(OffsetDateTime authorizedAt) { this.authorizedAt = authorizedAt; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime capturedAt) { this.capturedAt = capturedAt; }
}
