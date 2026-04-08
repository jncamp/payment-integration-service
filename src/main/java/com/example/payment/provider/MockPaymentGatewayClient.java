package com.example.payment.provider;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    @Value("${payment.webhook.secret:demo-secret}")
    private String webhookSecret;

    @Override
    public ProviderChargeResult charge(CreatePaymentRequest request) {
        if (request.getAmount().doubleValue() > 9999.99) {
            return new ProviderChargeResult(
                    "pi_" + UUID.randomUUID().toString().replace("-", ""),
                    false,
                    "Amount exceeds demo gateway limit"
            );
        }

        return new ProviderChargeResult(
                "pi_" + UUID.randomUUID().toString().replace("-", ""),
                true,
                null
        );
    }

    @Override
    public ProviderRefundResult refund(PaymentTransaction payment, String reason) {
        if (payment.getProviderPaymentId() == null) {
            return new ProviderRefundResult(null, false, "Payment has no provider payment id");
        }

        return new ProviderRefundResult(
                "re_" + UUID.randomUUID().toString().replace("-", ""),
                true,
                null
        );
    }

    @Override
    public boolean isValidWebhookSignature(String signature, String rawBody) {
        return signature != null && !signature.isBlank();
    }
}
