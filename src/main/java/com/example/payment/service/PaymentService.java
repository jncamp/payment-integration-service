package com.example.payment.service;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundPaymentRequest;
import com.example.payment.dto.WebhookEventRequest;
import com.example.payment.dto.stripe.*;
import com.example.payment.entity.*;
import com.example.payment.enums.*;
import com.example.payment.exception.ApiException;
import com.example.payment.provider.StripePaymentGatewayClient;
import com.example.payment.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String DEFAULT_CLIENT_CODE = "default";

    private final StripePaymentGatewayClient stripePaymentGatewayClient;
    private final PaymentIntentRepository paymentIntentRepository;
    private final CustomerRepository customerRepository;
    private final ApiClientRepository apiClientRepository;
    private final ChargeRepository chargeRepository;
    private final RefundRepository refundRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentIntentRepository paymentIntentRepository,
                          CustomerRepository customerRepository,
                          ApiClientRepository apiClientRepository,
                          ChargeRepository chargeRepository,
                          RefundRepository refundRepository,
                          WebhookEventRepository webhookEventRepository,
                          StripePaymentGatewayClient stripePaymentGatewayClient,
                          ObjectMapper objectMapper) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.customerRepository = customerRepository;
        this.apiClientRepository = apiClientRepository;
        this.chargeRepository = chargeRepository;
        this.refundRepository = refundRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.stripePaymentGatewayClient = stripePaymentGatewayClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        if (hasText(request.getPaymentMethodId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Do not pass paymentMethodId to /api/payments/create. Create the PaymentIntent first, then confirm it with /api/payment_intents/{id}/confirm.");
        }

        ApiClient client = getDefaultClient();
        if (hasText(request.getIdempotencyKey())) {
            var existing = paymentIntentRepository.findByClientAndIdempotencyKey(client, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return PaymentResponse.fromReplay(existing.get(), latestRefundId(existing.get()));
            }
        }

        Customer customer = getOrCreateCustomer(client, request.getCustomerEmail(), request.getCustomerName(), request.getCurrency());

        try {
            PaymentIntent stripeIntent = stripePaymentGatewayClient.createPaymentIntent(
                    toMinorUnits(request.getAmount()),
                    request.getCurrency().toLowerCase(),
                    writeMetadata(Map.of(
                            "customerEmail", request.getCustomerEmail(),
                            "customerName", request.getCustomerName()
                    ))
            );

            PaymentIntentEntity payment = new PaymentIntentEntity();
            payment.setClient(client);
            payment.setCustomer(customer);
            payment.setInternalReference("PAY-" + randomCompactId().substring(0, 18).toUpperCase());
            payment.setProvider(PaymentProvider.STRIPE);
            payment.setProviderPaymentIntentId(stripeIntent.getId());
            payment.setClientSecret(stripeIntent.getClientSecret());
            payment.setAmount(toMinorUnits(request.getAmount()));
            payment.setCurrency(request.getCurrency().toUpperCase());
            payment.setIdempotencyKey(request.getIdempotencyKey());
            payment.setMetadataJson(writeMetadata(Map.of(
                    "customerEmail", request.getCustomerEmail(),
                    "customerName", request.getCustomerName()
            )));

            PaymentStatus createdStatus = mapStripeIntentStatus(stripeIntent.getStatus());
            if (createdStatus == PaymentStatus.SUCCEEDED || createdStatus == PaymentStatus.PROCESSING) {
                createdStatus = PaymentStatus.REQUIRES_PAYMENT_METHOD;
            }
            payment.setStatus(createdStatus);
            payment = paymentIntentRepository.save(payment);

            return PaymentResponse.from(payment, latestRefundId(payment));
        } catch (StripeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe create payment failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        PaymentIntentEntity payment = findPayment(id);
        return PaymentResponse.from(payment, latestRefundId(payment));
    }

    @Transactional
    public PaymentResponse refundPayment(UUID id, RefundPaymentRequest request) {
        PaymentIntentEntity payment = findPayment(id);
        CreateRefundRequest refundRequest = new CreateRefundRequest();
        refundRequest.setPaymentIntentId(payment.getProviderPaymentIntentId());
        refundRequest.setReason(request.getReason());
        StripeRefundResponse refundResponse = createRefund(refundRequest);
        payment = findPayment(id);
        return PaymentResponse.from(payment, refundResponse.getId());
    }

    @Transactional
    public StripePaymentIntentResponse createPaymentIntent(CreatePaymentIntentRequest request, String idempotencyKey) {
        ApiClient client = getDefaultClient();
        if (hasText(idempotencyKey)) {
            var existing = paymentIntentRepository.findByClientAndIdempotencyKey(client, idempotencyKey);
            if (existing.isPresent()) {
                return toStripePaymentIntent(existing.get(), true);
            }
        }

        try {
            PaymentIntent stripeIntent = stripePaymentGatewayClient.createPaymentIntent(
                    request.getAmount(),
                    request.getCurrency().toLowerCase(),
                    writeMetadata(request.getMetadata())
            );
            Customer customer = getOrCreateCustomer(
                    client,
                    request.getCustomerEmail(),
                    request.getCustomerName(),
                    request.getCurrency().toUpperCase()
            );

            PaymentIntentEntity payment = new PaymentIntentEntity();
            payment.setClient(client);
            payment.setCustomer(customer);
            payment.setInternalReference("PI-" + randomCompactId().substring(0, 18).toUpperCase());
            payment.setProvider(PaymentProvider.STRIPE);
            payment.setProviderPaymentIntentId(stripeIntent.getId());
            payment.setClientSecret(stripeIntent.getClientSecret());
            payment.setAmount(request.getAmount());
            payment.setCurrency(request.getCurrency().toUpperCase());
            payment.setIdempotencyKey(idempotencyKey);
            payment.setMetadataJson(writeMetadata(request.getMetadata()));
            payment.setStatus(mapStripeIntentStatus(stripeIntent.getStatus()));
            payment = paymentIntentRepository.save(payment);
            return toStripePaymentIntent(payment, false);

        } catch (StripeException e) {
            Integer statusCode = e.getStatusCode();

            if (statusCode != null && statusCode >= 400 && statusCode < 500) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid payment intent request: " + e.getMessage()
                );
            }

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe create payment intent failed: " + e.getMessage()
            );
        }
    }
    
    @Transactional
    public StripePaymentIntentResponse confirmPaymentIntent(String paymentIntentId, ConfirmPaymentIntentRequest request) {
        PaymentIntentEntity payment = findByProviderPaymentId(paymentIntentId);
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return toStripePaymentIntent(payment, false);
        }

        try {
            PaymentIntent stripeIntent = stripePaymentGatewayClient.confirmPaymentIntent(
                    paymentIntentId,
                    request != null ? request.getPaymentMethodId() : null
            );
            payment = syncPaymentFromStripe(payment, stripeIntent);
            return toStripePaymentIntent(payment, false);
        } catch (StripeException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureMessage(e.getMessage());
            paymentIntentRepository.save(payment);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe confirm payment intent failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StripePaymentIntentResponse getPaymentIntent(String paymentIntentId) {
        return toStripePaymentIntent(findByProviderPaymentId(paymentIntentId), false);
    }

    @Transactional
    public StripeRefundResponse createRefund(CreateRefundRequest request) {
        PaymentIntentEntity payment = findByProviderPaymentId(request.getPaymentIntentId());
        if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only succeeded or partially refunded payment intents can be refunded");
        }

        try {
            ChargeEntity charge = findLatestCharge(payment);
            long alreadyRefunded = charge.getAmountRefunded() == null ? 0L : charge.getAmountRefunded();
            long remainingRefundable = payment.getAmount() - alreadyRefunded;
            if (remainingRefundable <= 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Payment is already fully refunded");
            }

            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder();
            if (hasText(charge.getProviderChargeId())) {
                paramsBuilder.setCharge(charge.getProviderChargeId());
            } else {
                paramsBuilder.setPaymentIntent(payment.getProviderPaymentIntentId());
            }

            long refundAmount = request.getAmount() != null ? request.getAmount() : remainingRefundable;
            if (refundAmount <= 0 || refundAmount > remainingRefundable) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Refund amount must be between 1 and remaining refundable amount");
            }
            paramsBuilder.setAmount(refundAmount);

            if (hasText(request.getReason())) {
                switch (request.getReason()) {
                    case "duplicate" -> paramsBuilder.setReason(RefundCreateParams.Reason.DUPLICATE);
                    case "fraudulent" -> paramsBuilder.setReason(RefundCreateParams.Reason.FRAUDULENT);
                    case "requested_by_customer" -> paramsBuilder.setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
                    default -> throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "Unsupported refund reason. Allowed values: duplicate, fraudulent, requested_by_customer"
                    );
                }
            }

            Refund stripeRefund = Refund.create(paramsBuilder.build());

            OffsetDateTime now = OffsetDateTime.now();

            RefundEntity refund = new RefundEntity();
            refund.setPaymentIntent(payment);
            refund.setCharge(charge);
            refund.setInternalReference("REF-" + randomCompactId().substring(0, 18).toUpperCase());
            refund.setProviderRefundId(stripeRefund.getId());
            refund.setAmount(stripeRefund.getAmount());
            refund.setCurrency(stripeRefund.getCurrency().toUpperCase());
            refund.setReason(stripeRefund.getReason());
            refund.setStatus(RefundStatus.SUCCEEDED);
            refund.setSucceededAt(now);
            refund.setCreatedAt(now);
            refund.setUpdatedAt(now);
            refund.setMetadataJson("{}");
            refund = refundRepository.save(refund);

            long newRefundedTotal = alreadyRefunded + stripeRefund.getAmount();
            charge.setAmountRefunded(newRefundedTotal);
            charge.setStatus(newRefundedTotal >= charge.getAmountCaptured() ? ChargeStatus.REFUNDED : ChargeStatus.PARTIALLY_REFUNDED);
            chargeRepository.save(charge);

            payment.setStatus(newRefundedTotal >= payment.getAmount() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            payment.setUpdatedAt(now);
            paymentIntentRepository.save(payment);

            StripeRefundResponse response = new StripeRefundResponse();
            response.setId(refund.getProviderRefundId());
            response.setObject("refund");
            response.setPaymentIntent(payment.getProviderPaymentIntentId());
            response.setCharge(charge.getProviderChargeId());
            response.setAmount(refund.getAmount());
            response.setCurrency(refund.getCurrency().toLowerCase());
            response.setReason(refund.getReason());
            response.setStatus(refund.getStatus().name().toLowerCase());
            response.setCreated(refund.getCreatedAt().toEpochSecond());
            return response;
        } catch (StripeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe refund failed: " + e.getMessage());
        }
    }

    public void handleStripePaymentIntentSucceeded(Event event) {
        String providerPaymentId = extractStripePaymentIntentId(event);
        processWebhookEvent(PaymentProvider.STRIPE, event.getId(), providerPaymentId, event.getType(), buildStripeWebhookPayload(event, providerPaymentId), true);
    }

    public void handleStripePaymentIntentFailed(Event event) {
        String providerPaymentId = extractStripePaymentIntentId(event);
        processWebhookEvent(PaymentProvider.STRIPE, event.getId(), providerPaymentId, event.getType(), buildStripeWebhookPayload(event, providerPaymentId), true);
    }

    @Transactional
    public StripeEventResponse processWebhook(String providerPaymentId, String eventType) {
        throw new ApiException(HttpStatus.GONE, "Generic payment-provider webhook is disabled. Use /api/webhooks/stripe only.");
    }

    public boolean hasValidWebhookSignature(String signature, String rawBody) {
        return false;
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
        data.setObject(toStripePaymentIntent(payment, false));
        event.setData(data);
        return event;
    }

    private PaymentIntentEntity findPayment(UUID id) {
        return paymentIntentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
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

    private ApiClient getDefaultClient() {
        return apiClientRepository.findByClientCode(DEFAULT_CLIENT_CODE)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Default API client was not seeded by migration V5"));
    }

    private Customer getOrCreateCustomer(ApiClient client, String email, String fullName, String currency) {
        return customerRepository.findByClientAndEmailIgnoreCase(client, email)
                .map(existing -> {
                    existing.setFullName(fullName);
                    existing.setDefaultCurrency(currency.toUpperCase());
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> {
                    Customer customer = new Customer();
                    customer.setClient(client);
                    customer.setEmail(email);
                    customer.setFullName(fullName);
                    customer.setDefaultCurrency(currency.toUpperCase());
                    customer.setMetadataJson("{}");
                    return customerRepository.save(customer);
                });
    }

    private StripePaymentIntentResponse toStripePaymentIntent(PaymentIntentEntity payment, boolean replay) {
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
        response.setIdempotentReplay(replay);
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

    private PaymentStatus mapStripeIntentStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "requires_confirmation" -> PaymentStatus.REQUIRES_CONFIRMATION;
            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "processing" -> PaymentStatus.PROCESSING;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELED;
            default -> PaymentStatus.REQUIRES_PAYMENT_METHOD;
        };
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

    private long toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private String writeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid metadata payload");
        }
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

    private String latestRefundId(PaymentIntentEntity payment) {
        return refundRepository.findTopByPaymentIntentOrderByCreatedAtDesc(payment)
                .map(RefundEntity::getProviderRefundId)
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String randomCompactId() {
        return UUID.randomUUID().toString().replace("-", "");
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
}
