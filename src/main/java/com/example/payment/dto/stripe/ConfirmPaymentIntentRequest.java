package com.example.payment.dto.stripe;

public class ConfirmPaymentIntentRequest {

    private String paymentMethodId;

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }
}
