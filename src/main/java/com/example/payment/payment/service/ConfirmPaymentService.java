package com.example.payment.payment.service;

import com.example.payment.dto.stripe.ConfirmPaymentIntentRequest;
import com.example.payment.dto.stripe.StripePaymentIntentResponse;
import com.example.payment.entity.ChargeEntity;
import com.example.payment.entity.PaymentIntentEntity;
import com.example.payment.enums.ChargeStatus;
import com.example.payment.enums.PaymentStatus;
import com.example.payment.shared.error.ApiException;
import com.example.payment.payment.mapper.StripePaymentIntentResponseMapper;
import com.example.payment.provider.PaymentProviderGateway;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.repository.PaymentIntentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ConfirmPaymentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final ChargeRepository chargeRepository;
    private final PaymentProviderGateway paymentProviderGateway;
    private final StripePaymentIntentResponseMapper stripePaymentIntentResponseMapper;

    public ConfirmPaymentService(PaymentIntentRepository paymentIntentRepository,
                                 ChargeRepository chargeRepository,
                                 PaymentProviderGateway paymentProviderGateway,
                                 StripePaymentIntentResponseMapper stripePaymentIntentResponseMapper) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.chargeRepository = chargeRepository;
        this.paymentProviderGateway = paymentProviderGateway;
        this.stripePaymentIntentResponseMapper = stripePaymentIntentResponseMapper;
    }

    @Transactional
    public StripePaymentIntentResponse confirmPaymentIntent(String paymentIntentId, ConfirmPaymentIntentRequest request) {
        PaymentIntentEntity payment = findByProviderPaymentId(paymentIntentId);
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            return stripePaymentIntentResponseMapper.toResponse(payment, false);
        }

        try {
            PaymentIntent stripeIntent = paymentProviderGateway.confirmPaymentIntent(
                    paymentIntentId,
                    request != null ? request.getPaymentMethodId() : null
            );
            payment = syncPaymentFromStripe(payment, stripeIntent);
            return stripePaymentIntentResponseMapper.toResponse(payment, false);
        } catch (StripeException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureMessage(e.getMessage());
            paymentIntentRepository.save(payment);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe confirm payment intent failed: " + e.getMessage());
        }
    }

    private PaymentIntentEntity syncPaymentFromStripe(PaymentIntentEntity payment, PaymentIntent stripeIntent) {
        ChargeEntity charge = findOrCreateCharge(payment, stripeIntent.getLatestCharge(), payment.getAmount(), payment.getCurrency());
        payment.setClientSecret(stripeIntent.getClientSecret());
        payment.setFailureMessage(stripeIntent.getLastPaymentError() != null ? stripeIntent.getLastPaymentError().getMessage() : null);
        payment.setStatus(mapStripeConfirmStatus(stripeIntent.getStatus()));
        payment.setConfirmedAt(OffsetDateTime.now());

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            payment.setSucceededAt(OffsetDateTime.now());
            charge.setStatus(ChargeStatus.CAPTURED);
            charge.setAmountCaptured(payment.getAmount());
            charge.setCapturedAt(OffsetDateTime.now());
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            charge.setStatus(ChargeStatus.FAILED);
            charge.setFailureMessage(payment.getFailureMessage());
        } else {
            charge.setStatus(ChargeStatus.PENDING);
        }

        charge = chargeRepository.save(charge);
        payment.setLatestChargeId(charge.getId());
        return paymentIntentRepository.save(payment);
    }

    private PaymentIntentEntity findByProviderPaymentId(String providerPaymentId) {
        return paymentIntentRepository.findByProviderPaymentIntentId(providerPaymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment intent not found"));
    }

    private ChargeEntity findLatestCharge(PaymentIntentEntity payment) {
        if (payment.getLatestChargeId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "No charge found for payment intent");
        }
        return chargeRepository.findById(payment.getLatestChargeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Charge not found"));
    }

    private ChargeEntity findOrCreateCharge(PaymentIntentEntity payment, String providerChargeId, long amount, String currency) {
        if (hasText(providerChargeId)) {
            return chargeRepository.findByProviderChargeId(providerChargeId)
                    .orElseGet(() -> buildCharge(payment, providerChargeId, amount, currency));
        }
        if (payment.getLatestChargeId() != null) {
            return findLatestCharge(payment);
        }
        return buildCharge(payment, null, amount, currency);
    }

    private ChargeEntity buildCharge(PaymentIntentEntity payment, String providerChargeId, long amount, String currency) {
        ChargeEntity charge = new ChargeEntity();
        charge.setPaymentIntent(payment);
        charge.setProviderChargeId(providerChargeId);
        charge.setAmountAuthorized(amount);
        charge.setAmountCaptured(0L);
        charge.setAmountRefunded(0L);
        charge.setCurrency(currency.toUpperCase());
        charge.setStatus(ChargeStatus.PENDING);
        charge.setAuthorizedAt(OffsetDateTime.now());
        return charge;
    }

    private PaymentStatus mapStripeConfirmStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "processing" -> PaymentStatus.PROCESSING;
            case "requires_payment_method", "canceled" -> PaymentStatus.FAILED;
            case "requires_action", "requires_confirmation", "requires_capture" -> PaymentStatus.PROCESSING;
            default -> PaymentStatus.PROCESSING;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
