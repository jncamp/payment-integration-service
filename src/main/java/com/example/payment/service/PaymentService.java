package com.example.payment.service;
import com.stripe.model.Event;
import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundPaymentRequest;
import com.example.payment.dto.stripe.*;
import com.example.payment.entity.PaymentTransaction;
import com.example.payment.enums.PaymentProvider;
import com.example.payment.enums.PaymentStatus;
import com.example.payment.exception.ApiException;
import com.example.payment.provider.PaymentGatewayClient;
import com.example.payment.provider.ProviderChargeResult;
import com.example.payment.provider.ProviderRefundResult;
import com.example.payment.provider.StripePaymentGatewayClient;
import com.example.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final StripePaymentGatewayClient stripePaymentGatewayClient;
    private final PaymentTransactionRepository repository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentTransactionRepository repository,
                          PaymentGatewayClient paymentGatewayClient,
                          StripePaymentGatewayClient stripePaymentGatewayClient,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.paymentGatewayClient = paymentGatewayClient;
        this.stripePaymentGatewayClient = stripePaymentGatewayClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<PaymentTransaction> existing = repository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return PaymentResponse.fromReplay(existing.get());
            }
        }

        PaymentTransaction payment = new PaymentTransaction();
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerEmail(request.getCustomerEmail());
        payment.setCustomerName(request.getCustomerName());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProvider(PaymentProvider.STRIPE);
        repository.save(payment);

        ProviderChargeResult result = paymentGatewayClient.charge(request);
        payment.setProviderPaymentId(result.getProviderPaymentId());
        payment.setLatestChargeId("ch_" + randomCompactId());

        if (result.isSuccess()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setFailureReason(null);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.getFailureReason());
        }

        return PaymentResponse.from(repository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        return PaymentResponse.from(findPayment(id));
    }

    @Transactional
    public PaymentResponse refundPayment(UUID id, RefundPaymentRequest request) {
        PaymentTransaction payment = findPayment(id);

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only SUCCEEDED payments can be refunded");
        }

        ProviderRefundResult result = paymentGatewayClient.refund(payment, request.getReason());
        if (!result.isSuccess()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, result.getFailureReason());
        }

        payment.setProviderRefundId(result.getProviderRefundId());
        payment.setStatus(PaymentStatus.REFUNDED);
        return PaymentResponse.from(repository.save(payment));
    }

    @Transactional
    public StripePaymentIntentResponse createPaymentIntent(CreatePaymentIntentRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentTransaction> existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toStripePaymentIntent(existing.get(), true);
            }
        }

        try {
            PaymentIntent stripeIntent = stripePaymentGatewayClient.createPaymentIntent(
                    request.getAmount(),
                    request.getCurrency().toLowerCase()
            );

            PaymentTransaction payment = new PaymentTransaction();
            payment.setAmount(toDecimalAmount(request.getAmount()));
            payment.setCurrency(request.getCurrency().toUpperCase());
            payment.setCustomerEmail(request.getCustomerEmail());
            payment.setCustomerName(request.getCustomerName());
            payment.setIdempotencyKey(idempotencyKey);
            payment.setProvider(PaymentProvider.STRIPE);
            payment.setProviderPaymentId(stripeIntent.getId());
            payment.setClientSecret(stripeIntent.getClientSecret());
            payment.setMetadataJson(writeMetadata(request.getMetadata()));

            String stripeStatus = stripeIntent.getStatus();
            if ("requires_confirmation".equals(stripeStatus)) {
                payment.setStatus(PaymentStatus.REQUIRES_CONFIRMATION);
            } else if ("requires_action".equals(stripeStatus)) {
                payment.setStatus(PaymentStatus.REQUIRES_ACTION);
            } else if ("processing".equals(stripeStatus)) {
                payment.setStatus(PaymentStatus.PROCESSING);
            } else if ("succeeded".equals(stripeStatus)) {
                payment.setStatus(PaymentStatus.SUCCEEDED);
            } else if ("canceled".equals(stripeStatus)) {
                payment.setStatus(PaymentStatus.CANCELED);
            } else {
                payment.setStatus(PaymentStatus.REQUIRES_PAYMENT_METHOD);
            }

            return toStripePaymentIntent(repository.save(payment), false);

        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe create payment intent failed: " + e.getMessage());
        }
    }

    @Transactional
    public StripePaymentIntentResponse confirmPaymentIntent(
            String paymentIntentId,
            ConfirmPaymentIntentRequest request
    ) {
        PaymentTransaction payment = findByProviderPaymentId(paymentIntentId);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return toStripePaymentIntent(payment, false);
        }

        try {
            PaymentIntent stripeIntent = PaymentIntent.retrieve(paymentIntentId);

            PaymentIntentConfirmParams.Builder paramsBuilder = PaymentIntentConfirmParams.builder();

            if (request != null
                    && request.getPaymentMethodId() != null
                    && !request.getPaymentMethodId().isBlank()) {
                paramsBuilder.setPaymentMethod(request.getPaymentMethodId().trim());
            }

            stripeIntent = stripeIntent.confirm(paramsBuilder.build());

            payment.setLatestChargeId(stripeIntent.getLatestCharge());
            payment.setFailureReason(
                    stripeIntent.getLastPaymentError() != null
                            ? stripeIntent.getLastPaymentError().getMessage()
                            : null
            );

            switch (stripeIntent.getStatus()) {
                case "succeeded" -> payment.setStatus(PaymentStatus.SUCCEEDED);
                case "processing" -> payment.setStatus(PaymentStatus.PROCESSING);
                case "requires_payment_method", "canceled" -> payment.setStatus(PaymentStatus.FAILED);
                case "requires_action", "requires_confirmation", "requires_capture" ->
                        payment.setStatus(PaymentStatus.PROCESSING);
                default -> payment.setStatus(PaymentStatus.PROCESSING);
            }

            repository.save(payment);
            return toStripePaymentIntent(payment, false);

        } catch (StripeException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            repository.save(payment);
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Stripe confirm payment intent failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StripePaymentIntentResponse getPaymentIntent(String paymentIntentId) {
        return toStripePaymentIntent(findByProviderPaymentId(paymentIntentId), false);
    }

    @Transactional
    public StripeRefundResponse createRefund(CreateRefundRequest request) {
        PaymentTransaction payment = findByProviderPaymentId(request.getPaymentIntentId());

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only succeeded payment intents can be refunded");
        }

        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder();

            if (payment.getLatestChargeId() != null && !payment.getLatestChargeId().isBlank()) {
                paramsBuilder.setCharge(payment.getLatestChargeId());
            } else if (payment.getProviderPaymentId() != null && !payment.getProviderPaymentId().isBlank()) {
                paramsBuilder.setPaymentIntent(payment.getProviderPaymentId());
            } else {
                throw new ApiException(HttpStatus.CONFLICT,
                        "No Stripe charge or payment intent available for refund");
            }

            if (request.getAmount() != null) {
                long refundAmount = request.getAmount();

                if (refundAmount <= 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Refund amount must be greater than zero");
                }

                if (refundAmount > toMinorUnits(payment.getAmount())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Refund amount cannot exceed original payment amount");
                }

                paramsBuilder.setAmount(refundAmount);
            }

            if (request.getReason() != null && !request.getReason().isBlank()) {
                switch (request.getReason()) {
                    case "duplicate" ->
                            paramsBuilder.setReason(RefundCreateParams.Reason.DUPLICATE);
                    case "fraudulent" ->
                            paramsBuilder.setReason(RefundCreateParams.Reason.FRAUDULENT);
                    case "requested_by_customer" ->
                            paramsBuilder.setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
                    default -> {
                        // ignore unsupported values and let Stripe use no reason
                    }
                }
            }

            Refund refund = Refund.create(paramsBuilder.build());

            payment.setProviderRefundId(refund.getId());
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setFailureReason(null);

            PaymentTransaction saved = repository.save(payment);

            StripeRefundResponse response = new StripeRefundResponse();
            response.setId(refund.getId());
            response.setObject("refund");
            response.setPaymentIntent(saved.getProviderPaymentId());
            response.setCharge(refund.getCharge());
            response.setAmount(refund.getAmount());
            response.setCurrency(refund.getCurrency());
            response.setReason(refund.getReason());
            response.setStatus(refund.getStatus());
            response.setCreated(refund.getCreated());
            return response;

        } catch (StripeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Stripe refund failed: " + e.getMessage());
        }
    }
    public void handleStripePaymentIntentSucceeded(Event event) {
        System.out.println("SUCCESS: " + event.getId());
    }

    public void handleStripePaymentIntentFailed(Event event) {
        System.out.println("FAILED: " + event.getId());
    }
    @Transactional
    public StripeEventResponse processWebhook(String providerPaymentId, String eventType) {
        PaymentTransaction payment = repository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found for provider payment id"));

        switch (eventType) {
            case "payment_intent.succeeded" -> {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setFailureReason(null);
            }
            case "payment_intent.processing" -> payment.setStatus(PaymentStatus.PROCESSING);
            case "payment_intent.payment_failed" -> payment.setStatus(PaymentStatus.FAILED);
            case "payment_intent.canceled" -> payment.setStatus(PaymentStatus.CANCELED);
            case "charge.refunded" -> payment.setStatus(PaymentStatus.REFUNDED);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported eventType: " + eventType);
        }

        PaymentTransaction saved = repository.save(payment);
        StripeEventResponse event = new StripeEventResponse();
        event.setId("evt_" + randomCompactId());
        event.setObject("event");
        event.setType(eventType);
        event.setCreated(saved.getUpdatedAt().toEpochSecond());
        StripeEventResponse.EventData data = new StripeEventResponse.EventData();
        data.setObject(toStripePaymentIntent(saved, false));
        event.setData(data);
        return event;
    }

    public boolean hasValidWebhookSignature(String signature, String rawBody) {
        return paymentGatewayClient.isValidWebhookSignature(signature, rawBody);
    }

    private PaymentTransaction findPayment(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private PaymentTransaction findByProviderPaymentId(String providerPaymentId) {
        return repository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment intent not found"));
    }

    private StripePaymentIntentResponse toStripePaymentIntent(PaymentTransaction payment, boolean replay) {
        StripePaymentIntentResponse response = new StripePaymentIntentResponse();
        response.setId(payment.getProviderPaymentId());
        response.setObject("payment_intent");
        response.setAmount(toMinorUnits(payment.getAmount()));
        response.setCurrency(payment.getCurrency().toLowerCase());
        response.setStatus(toStripeStatus(payment.getStatus()));
        response.setClientSecret(payment.getClientSecret());
        response.setCustomerName(payment.getCustomerName());
        response.setCustomerEmail(payment.getCustomerEmail());
        response.setMetadata(readMetadata(payment.getMetadataJson()));
        response.setCreated(payment.getCreatedAt().atZoneSameInstant(ZoneOffset.UTC).toEpochSecond());
        response.setLatestCharge(payment.getLatestChargeId());
        response.setIdempotentReplay(replay);
        return response;
    }

    private String toStripeStatus(PaymentStatus status) {
        return switch (status) {
            case REQUIRES_PAYMENT_METHOD -> "requires_payment_method";
            case REQUIRES_CONFIRMATION -> "requires_confirmation";
            case REQUIRES_ACTION -> "requires_action";
            case PROCESSING -> "processing";
            case SUCCEEDED -> "succeeded";
            case FAILED -> "payment_failed";
            case CANCELED -> "canceled";
            case REFUNDED -> "refunded";
        };
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private BigDecimal toDecimalAmount(Long amountMinorUnits) {
        return BigDecimal.valueOf(amountMinorUnits).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String writeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid metadata payload");
        }
    }

    private Map<String, String> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private String randomCompactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
