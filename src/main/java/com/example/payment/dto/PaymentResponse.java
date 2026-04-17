package com.example.payment.dto;

import com.example.payment.entity.PaymentIntentEntity;
import com.example.payment.enums.PaymentProvider;
import com.example.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PaymentResponse {

    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String customerEmail;
    private String customerName;
    private PaymentStatus status;
    private PaymentProvider provider;
    private String providerPaymentId;
    private String clientSecret;
    private String providerRefundId;
    private String failureReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean idempotentReplay;

    public static PaymentResponse from(PaymentIntentEntity payment, String providerRefundId) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setAmount(BigDecimal.valueOf(payment.getAmount()).divide(BigDecimal.valueOf(100)));
        response.setCurrency(payment.getCurrency());
        response.setCustomerEmail(payment.getCustomer() != null ? payment.getCustomer().getEmail() : null);
        response.setCustomerName(payment.getCustomer() != null ? payment.getCustomer().getFullName() : null);
        response.setStatus(payment.getStatus());
        response.setProvider(payment.getProvider());
        response.setProviderPaymentId(payment.getProviderPaymentIntentId());
        response.setClientSecret(payment.getClientSecret());
        response.setProviderRefundId(providerRefundId);
        response.setFailureReason(payment.getFailureMessage());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        response.setIdempotentReplay(false);
        return response;
    }

    public static PaymentResponse fromReplay(PaymentIntentEntity payment, String providerRefundId) {
        PaymentResponse response = from(payment, providerRefundId);
        response.setIdempotentReplay(true);
        return response;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
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
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) { this.providerRefundId = providerRefundId; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isIdempotentReplay() { return idempotentReplay; }
    public void setIdempotentReplay(boolean idempotentReplay) { this.idempotentReplay = idempotentReplay; }
}
