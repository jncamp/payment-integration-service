package com.example.payment.service;

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
import com.example.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final PaymentTransactionRepository repository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentTransactionRepository repository,
                          PaymentGatewayClient paymentGatewayClient,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.paymentGatewayClient = paymentGatewayClient;
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

        PaymentTransaction payment = new PaymentTransaction();
        payment.setAmount(toDecimalAmount(request.getAmount()));
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setCustomerEmail(request.getCustomerEmail());
        payment.setCustomerName(request.getCustomerName());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus(PaymentStatus.REQUIRES_CONFIRMATION);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setProviderPaymentId("pi_" + randomCompactId());
        payment.setClientSecret(payment.getProviderPaymentId() + "_secret_" + randomCompactId());
        payment.setMetadataJson(writeMetadata(request.getMetadata()));
        return toStripePaymentIntent(repository.save(payment), false);
    }

    @Transactional
    public StripePaymentIntentResponse confirmPaymentIntent(String paymentIntentId, ConfirmPaymentIntentRequest request) {
        PaymentTransaction payment = findByProviderPaymentId(paymentIntentId);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return toStripePaymentIntent(payment, false);
        }

        payment.setStatus(PaymentStatus.PROCESSING);
        repository.save(payment);

        CreatePaymentRequest chargeRequest = new CreatePaymentRequest();
        chargeRequest.setAmount(payment.getAmount());
        chargeRequest.setCurrency(payment.getCurrency());
        chargeRequest.setCustomerEmail(payment.getCustomerEmail());
        chargeRequest.setCustomerName(payment.getCustomerName());

        ProviderChargeResult result = paymentGatewayClient.charge(chargeRequest);
        payment.setLatestChargeId("ch_" + randomCompactId());

        if (result.isSuccess()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setFailureReason(null);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.getFailureReason());
        }

        return toStripePaymentIntent(repository.save(payment), false);
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

        ProviderRefundResult result = paymentGatewayClient.refund(payment, request.getReason());
        if (!result.isSuccess()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, result.getFailureReason());
        }

        payment.setProviderRefundId(result.getProviderRefundId());
        payment.setStatus(PaymentStatus.REFUNDED);
        PaymentTransaction saved = repository.save(payment);

        StripeRefundResponse response = new StripeRefundResponse();
        response.setId(saved.getProviderRefundId());
        response.setObject("refund");
        response.setPaymentIntent(saved.getProviderPaymentId());
        response.setCharge(saved.getLatestChargeId());
        response.setAmount(toMinorUnits(saved.getAmount()));
        response.setCurrency(saved.getCurrency().toLowerCase());
        response.setReason(request.getReason());
        response.setStatus("succeeded");
        response.setCreated(saved.getUpdatedAt().toEpochSecond());
        return response;
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
