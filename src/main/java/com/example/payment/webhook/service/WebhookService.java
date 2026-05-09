package com.example.payment.webhook.service;

import com.example.payment.webhook.dto.WebhookEventRequest;
import com.example.payment.dto.stripe.StripeEventResponse;
import com.example.payment.dto.stripe.StripePaymentIntentResponse;
import com.example.payment.entity.ApiClient;
import com.example.payment.entity.ChargeEntity;
import com.example.payment.entity.PaymentIntentEntity;
import com.example.payment.webhook.entity.WebhookEventEntity;
import com.example.payment.enums.ChargeStatus;
import com.example.payment.enums.PaymentProvider;
import com.example.payment.enums.PaymentStatus;
import com.example.payment.enums.WebhookStatus;
import com.example.payment.shared.error.ApiException;
import com.example.payment.repository.ApiClientRepository;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.repository.PaymentIntentRepository;
import com.example.payment.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;

@Service
public class WebhookService {

    private static final String DEFAULT_CLIENT_CODE = "default";

    private final ApiClientRepository apiClientRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final ChargeRepository chargeRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    public WebhookService(ApiClientRepository apiClientRepository,
                          PaymentIntentRepository paymentIntentRepository,
                          ChargeRepository chargeRepository,
                          WebhookEventRepository webhookEventRepository,
                          ObjectMapper objectMapper) {
        this.apiClientRepository = apiClientRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.chargeRepository = chargeRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleStripePaymentIntentSucceeded(Event event) {
        handleStripePaymentIntentEvent(event);
    }

    @Transactional
    public void handleStripePaymentIntentFailed(Event event) {
        handleStripePaymentIntentEvent(event);
    }

    private void handleStripePaymentIntentEvent(Event event) {
        String providerPaymentId = extractStripePaymentIntentId(event);
        processWebhookEvent(
                PaymentProvider.STRIPE,
                event.getId(),
                providerPaymentId,
                event.getType(),
                buildStripeWebhookPayload(event, providerPaymentId),
                true
        );
    }

    private StripeEventResponse processWebhookEvent(PaymentProvider provider, String providerEventId, String providerPaymentId,
                                                    String eventType, String rawPayload, boolean signatureValid) {
        ApiClient client = getDefaultClient();
        if (webhookEventRepository.findByProviderAndProviderEventId(provider, providerEventId).isPresent()) {
            PaymentIntentEntity existing = findByProviderPaymentId(providerPaymentId);
            return buildWebhookResponse(providerEventId, eventType, existing);
        }

        WebhookEventEntity event = new WebhookEventEntity();
        event.setClient(client);
        event.setProvider(provider);
        event.setProviderEventId(providerEventId);
        event.setEventType(eventType);
        event.setObjectType("payment_intent");
        event.setObjectId(providerPaymentId);
        event.setSignatureValid(signatureValid);
        event.setPayloadJson(rawPayload);
        event.setStatus(WebhookStatus.RECEIVED);
        event.setReceivedAt(OffsetDateTime.now());
        webhookEventRepository.save(event);

        try {
            PaymentIntentEntity payment = findByProviderPaymentId(providerPaymentId);
            ChargeEntity charge = payment.getLatestChargeId() != null ? findLatestCharge(payment) : null;
            switch (eventType) {
                case "payment_intent.succeeded" -> {
                    payment.setStatus(PaymentStatus.SUCCEEDED);
                    payment.setFailureMessage(null);
                    payment.setSucceededAt(OffsetDateTime.now());
                    if (charge != null) {
                        charge.setStatus(ChargeStatus.CAPTURED);
                        charge.setAmountCaptured(payment.getAmount());
                        charge.setCapturedAt(OffsetDateTime.now());
                        chargeRepository.save(charge);
                    }
                }
                case "payment_intent.processing" -> payment.setStatus(PaymentStatus.PROCESSING);
                case "payment_intent.payment_failed" -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    if (charge != null) {
                        charge.setStatus(ChargeStatus.FAILED);
                        chargeRepository.save(charge);
                    }
                }
                case "payment_intent.canceled" -> {
                    payment.setStatus(PaymentStatus.CANCELED);
                    payment.setCanceledAt(OffsetDateTime.now());
                }
                case "charge.refunded" -> {
                    if (charge != null) {
                        charge.setAmountRefunded(charge.getAmountCaptured());
                        charge.setStatus(ChargeStatus.REFUNDED);
                        chargeRepository.save(charge);
                    }
                    payment.setStatus(PaymentStatus.REFUNDED);
                }
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported eventType: " + eventType);
            }
            paymentIntentRepository.save(payment);
            event.setStatus(WebhookStatus.PROCESSED);
            event.setProcessedAt(OffsetDateTime.now());
            webhookEventRepository.save(event);
            return buildWebhookResponse(providerEventId, eventType, payment);
        } catch (RuntimeException ex) {
            event.setStatus(WebhookStatus.FAILED);
            event.setProcessingError(ex.getMessage());
            event.setProcessedAt(OffsetDateTime.now());
            webhookEventRepository.save(event);
            throw ex;
        }
    }

    private StripeEventResponse buildWebhookResponse(String eventId, String eventType, PaymentIntentEntity payment) {
        StripeEventResponse event = new StripeEventResponse();
        event.setId(eventId);
        event.setObject("event");
        event.setType(eventType);
        event.setCreated(OffsetDateTime.now().toEpochSecond());
        StripeEventResponse.EventData data = new StripeEventResponse.EventData();
        data.setObject(toStripePaymentIntent(payment));
        event.setData(data);
        return event;
    }

    private StripePaymentIntentResponse toStripePaymentIntent(PaymentIntentEntity payment) {
        StripePaymentIntentResponse response = new StripePaymentIntentResponse();
        response.setId(payment.getProviderPaymentIntentId());
        response.setObject("payment_intent");
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency().toLowerCase());
        response.setStatus(toStripeStatus(payment.getStatus()));
        response.setClientSecret(payment.getClientSecret());
        response.setCustomerName(payment.getCustomer() != null ? payment.getCustomer().getFullName() : null);
        response.setCustomerEmail(payment.getCustomer() != null ? payment.getCustomer().getEmail() : null);
        response.setMetadata(readMetadata(payment.getMetadataJson()));
        response.setCreated(payment.getCreatedAt() != null ? payment.getCreatedAt().atZoneSameInstant(ZoneOffset.UTC).toEpochSecond() : null);
        response.setLatestCharge(resolveLatestProviderChargeId(payment));
        response.setRefundedAmount(payment.getLatestChargeId() != null ? findLatestCharge(payment).getAmountRefunded() : 0L);
        response.setIdempotentReplay(false);
        return response;
    }

    private String resolveLatestProviderChargeId(PaymentIntentEntity payment) {
        if (payment.getLatestChargeId() == null) {
            return null;
        }
        return findLatestCharge(payment).getProviderChargeId();
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
            case PARTIALLY_REFUNDED -> "partially_refunded";
            case REFUNDED -> "refunded";
        };
    }

    private ApiClient getDefaultClient() {
        return apiClientRepository.findByClientCode(DEFAULT_CLIENT_CODE)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Default API client was not seeded by migration V5"));
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

    private Map<String, String> readMetadata(String metadataJson) {
        if (!hasText(metadataJson) || "{}".equals(metadataJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private String extractStripePaymentIntentId(Event event) {
        try {
            String rawJson = event.getDataObjectDeserializer().getRawJson();
            if (hasText(rawJson)) {
                WebhookEventRequest request = WebhookEventRequest.fromJson(rawJson);
                if (hasText(request.getProviderPaymentId())) {
                    return request.getProviderPaymentId();
                }
            }
        } catch (Exception ignored) {
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to extract payment intent id from Stripe webhook event");
    }

    private String buildStripeWebhookPayload(Event event, String providerPaymentId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "providerEventId", event.getId(),
                    "providerPaymentId", providerPaymentId,
                    "eventType", event.getType()
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
