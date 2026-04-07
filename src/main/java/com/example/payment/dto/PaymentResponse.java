package com.example.payment.dto;

import com.example.payment.entity.PaymentTransaction;
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
    private String providerRefundId;
    private String failureReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static PaymentResponse from(PaymentTransaction payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setCustomerEmail(payment.getCustomerEmail());
        response.setCustomerName(payment.getCustomerName());
        response.setStatus(payment.getStatus());
        response.setProvider(payment.getProvider());
        response.setProviderPaymentId(payment.getProviderPaymentId());
        response.setProviderRefundId(payment.getProviderRefundId());
        response.setFailureReason(payment.getFailureReason());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
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
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) { this.providerRefundId = providerRefundId; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
