package com.example.payment.provider;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;

/**
 * Provider boundary for payment operations.
 *
 * Business services depend on this interface instead of depending directly on
 * a concrete Stripe client. That keeps provider-specific SDK calls behind one
 * replaceable adapter and prepares the application for additional providers
 * such as PayPal, Square, or Authorize.Net.
 */
public interface PaymentProviderGateway {

    PaymentIntent createPaymentIntent(Long amount, String currency, String metadataJson) throws StripeException;

    PaymentIntent confirmPaymentIntent(String paymentIntentId, String paymentMethodId) throws StripeException;

    Refund createRefund(String providerChargeId,
                        String providerPaymentIntentId,
                        long amount,
                        String reason) throws StripeException;
}
