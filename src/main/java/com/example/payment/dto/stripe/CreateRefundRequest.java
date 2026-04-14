package com.example.payment.dto.stripe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateRefundRequest {

    @NotBlank(message = "paymentIntentId is required")
    private String paymentIntentId;

    // Amount in MINOR UNITS (e.g. cents)
    // Optional → if null, full refund
    @Positive(message = "amount must be greater than 0")
    private Long amount;

    @Size(max = 255, message = "reason must be 255 characters or less")
    private String reason;

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
