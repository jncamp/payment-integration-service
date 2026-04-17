package com.example.payment.provider;

import com.example.payment.dto.CreatePaymentRequest;

public interface PaymentGatewayClient {
    ProviderChargeResult charge(CreatePaymentRequest request);
    ProviderRefundResult refund(String providerPaymentIntentId, String reason);
    boolean isValidWebhookSignature(String signature, String rawBody);
}
