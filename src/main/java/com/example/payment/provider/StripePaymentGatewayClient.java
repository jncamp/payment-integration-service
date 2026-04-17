package com.example.payment.provider;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentConfirmParams;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.param.PaymentIntentCreateParams;

import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGatewayClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripePaymentGatewayClient(@Value("${stripe.secret-key}") String secretKey) {
        String cleanedKey = secretKey == null ? null : secretKey.trim();
        System.out.println("Stripe key prefix = " +
                (cleanedKey == null ? "null" : cleanedKey.substring(0, Math.min(12, cleanedKey.length()))));
        Stripe.apiKey = cleanedKey;
    }

    public PaymentIntent createPaymentIntent(Long amount, String currency, String metadataJson) throws StripeException {
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .setConfirm(false)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(
                                        PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                )
                                .build()
                );

        for (Map.Entry<String, String> entry : parseMetadata(metadataJson).entrySet()) {
            builder.putMetadata(entry.getKey(), entry.getValue());
        }

        return PaymentIntent.create(builder.build());
    }

    private Map<String, String> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank() || "{}".equals(metadataJson.trim())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    public PaymentIntent confirmPaymentIntent(String paymentIntentId, String paymentMethodId) throws StripeException {
        PaymentIntent stripeIntent = PaymentIntent.retrieve(paymentIntentId);
        PaymentIntentConfirmParams.Builder paramsBuilder = PaymentIntentConfirmParams.builder();
        if (paymentMethodId != null && !paymentMethodId.isBlank()) {
            paramsBuilder.setPaymentMethod(paymentMethodId.trim());
        }
        return stripeIntent.confirm(paramsBuilder.build());
    }
}
