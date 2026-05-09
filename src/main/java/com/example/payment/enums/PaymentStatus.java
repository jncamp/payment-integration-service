package com.example.payment.enums;

public enum PaymentStatus {
    REQUIRES_PAYMENT_METHOD,
    REQUIRES_CONFIRMATION,
    REQUIRES_ACTION,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
