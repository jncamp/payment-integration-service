package com.example.payment.provider;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.entity.PaymentTransaction;

public interface PaymentGatewayClient {
    ProviderChargeResult charge(CreatePaymentRequest request);
    ProviderRefundResult refund(PaymentTransaction payment, String reason);
    boolean isValidWebhookSignature(String signature, String rawBody);
}
