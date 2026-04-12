package com.example.payment.dto.stripe;

public class ConfirmPaymentIntentRequest {
    private String paymentMethod;

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
