package com.example.payment.dto.stripe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateRefundRequest {
    @NotBlank(message = "paymentIntentId is required")
    private String paymentIntentId;

    @Size(max = 255, message = "reason must be 255 characters or less")
    private String reason;

    public String getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(String paymentIntentId) { this.paymentIntentId = paymentIntentId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
