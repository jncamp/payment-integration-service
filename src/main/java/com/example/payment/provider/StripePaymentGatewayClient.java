package com.example.payment.provider;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGatewayClient {

    public StripePaymentGatewayClient(@Value("${stripe.secret-key}") String secretKey) {
        String cleanedKey = secretKey == null ? null : secretKey.trim();
        System.out.println("Stripe key prefix = " +
                (cleanedKey == null ? "null" : cleanedKey.substring(0, Math.min(12, cleanedKey.length()))));
        Stripe.apiKey = cleanedKey;
    }

    public PaymentIntent createPaymentIntent(Long amount, String currency) throws Exception {
        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(amount)
                        .setCurrency(currency)
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .setAllowRedirects(
                                                PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                        )
                                        .build()
                        )
                        .build();

        return PaymentIntent.create(params);
    }
}
