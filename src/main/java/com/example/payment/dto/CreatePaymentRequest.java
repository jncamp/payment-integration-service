package com.example.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreatePaymentRequest {
    private String idempotencyKey;
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.50", message = "amount must be at least 0.50")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter uppercase ISO code")
    private String currency;

    @NotBlank(message = "customerEmail is required")
    @Email(message = "customerEmail must be a valid email address")
    @Size(max = 120, message = "customerEmail must be 120 characters or less")
    private String customerEmail;

    @NotBlank(message = "customerName is required")
    @Size(max = 120, message = "customerName must be 120 characters or less")
    private String customerName;

    @Size(max = 120, message = "paymentMethodId must be 120 characters or less")
    private String paymentMethodId;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }
}
