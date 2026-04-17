package com.example.payment.provider;

import com.example.payment.dto.CreatePaymentRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

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
    public ProviderRefundResult refund(String providerPaymentIntentId, String reason) {
        if (providerPaymentIntentId == null || providerPaymentIntentId.isBlank()) {
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
