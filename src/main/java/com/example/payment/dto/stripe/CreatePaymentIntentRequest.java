package com.example.payment.dto.stripe;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public class CreatePaymentIntentRequest {

    @NotNull(message = "amount is required")
    @Min(value = 1, message = "amount must be greater than 0")
    private Long amount;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotBlank(message = "customerName is required")
    @Size(max = 120, message = "customerName must be 120 characters or less")
    private String customerName;

    @NotBlank(message = "customerEmail is required")
    @Email(message = "customerEmail must be a valid email address")
    @Size(max = 120, message = "customerEmail must be 120 characters or less")
    private String customerEmail;

    private Map<String, String> metadata;

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
