package com.example.payment.provider;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.model.Refund;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGatewayClient implements PaymentProviderGateway {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripePaymentGatewayClient(@Value("${stripe.secret-key}") String secretKey) {
        Stripe.apiKey = secretKey == null ? null : secretKey.trim();
    }

    @Override
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

    @Override
    public PaymentIntent confirmPaymentIntent(String paymentIntentId, String paymentMethodId) throws StripeException {
        PaymentIntent stripeIntent = PaymentIntent.retrieve(paymentIntentId);
        PaymentIntentConfirmParams.Builder paramsBuilder = PaymentIntentConfirmParams.builder();
        if (paymentMethodId != null && !paymentMethodId.isBlank()) {
            paramsBuilder.setPaymentMethod(paymentMethodId.trim());
        }
        return stripeIntent.confirm(paramsBuilder.build());
    }

    @Override
    public Refund createRefund(String providerChargeId,
                               String providerPaymentIntentId,
                               long amount,
                               String reason) throws StripeException {
        RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                .setAmount(amount);

        if (providerChargeId != null && !providerChargeId.isBlank()) {
            paramsBuilder.setCharge(providerChargeId);
        } else {
            paramsBuilder.setPaymentIntent(providerPaymentIntentId);
        }

        RefundCreateParams.Reason stripeReason = StripeRefundReasonMapper.toStripeReason(reason);
        if (stripeReason != null) {
            paramsBuilder.setReason(stripeReason);
        }

        return Refund.create(paramsBuilder.build());
    }
}
