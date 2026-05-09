package com.example.payment.payment.service;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.stripe.ConfirmPaymentIntentRequest;
import com.example.payment.dto.stripe.CreatePaymentIntentRequest;
import com.example.payment.dto.stripe.StripePaymentIntentResponse;
import com.example.payment.entity.*;
import com.example.payment.enums.*;
import com.example.payment.shared.error.ApiException;
import com.example.payment.provider.PaymentProviderGateway;
import com.example.payment.refund.service.RefundService;
import com.example.payment.payment.mapper.StripePaymentIntentResponseMapper;
import com.example.payment.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final String DEFAULT_CLIENT_CODE = "default";

    private final PaymentProviderGateway paymentProviderGateway;
    private final PaymentIntentRepository paymentIntentRepository;
    private final CustomerRepository customerRepository;
    private final ApiClientRepository apiClientRepository;
    private final ObjectMapper objectMapper;
    private final StripePaymentIntentResponseMapper stripePaymentIntentResponseMapper;
    private final ConfirmPaymentService confirmPaymentService;
    private final RefundService refundService;

    public PaymentService(PaymentIntentRepository paymentIntentRepository,
                          CustomerRepository customerRepository,
                          ApiClientRepository apiClientRepository,
                          PaymentProviderGateway paymentProviderGateway,
                          ObjectMapper objectMapper,
                          StripePaymentIntentResponseMapper stripePaymentIntentResponseMapper,
                          ConfirmPaymentService confirmPaymentService,
                          RefundService refundService) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.customerRepository = customerRepository;
        this.apiClientRepository = apiClientRepository;
        this.paymentProviderGateway = paymentProviderGateway;
        this.objectMapper = objectMapper;
        this.stripePaymentIntentResponseMapper = stripePaymentIntentResponseMapper;
        this.confirmPaymentService = confirmPaymentService;
        this.refundService = refundService;
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
            PaymentIntent stripeIntent = paymentProviderGateway.createPaymentIntent(
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
    public StripePaymentIntentResponse createPaymentIntent(CreatePaymentIntentRequest request, String idempotencyKey) {
        ApiClient client = getDefaultClient();
        if (hasText(idempotencyKey)) {
            var existing = paymentIntentRepository.findByClientAndIdempotencyKey(client, idempotencyKey);
            if (existing.isPresent()) {
                return stripePaymentIntentResponseMapper.toResponse(existing.get(), true);
            }
        }

        try {
            PaymentIntent stripeIntent = paymentProviderGateway.createPaymentIntent(
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
            return stripePaymentIntentResponseMapper.toResponse(payment, false);

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
        return confirmPaymentService.confirmPaymentIntent(paymentIntentId, request);
    }

    @Transactional(readOnly = true)
    public StripePaymentIntentResponse getPaymentIntent(String paymentIntentId) {
        return stripePaymentIntentResponseMapper.toResponse(findByProviderPaymentId(paymentIntentId), false);
    }

    private PaymentIntentEntity findPayment(UUID id) {
        return paymentIntentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private PaymentIntentEntity findByProviderPaymentId(String providerPaymentId) {
        return paymentIntentRepository.findByProviderPaymentIntentId(providerPaymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment intent not found"));
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

    private String latestRefundId(PaymentIntentEntity payment) {
        return refundService.latestRefundId(payment);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String randomCompactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
