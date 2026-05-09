package com.example.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RefundPaymentRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 255, message = "reason must be 255 characters or less")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
