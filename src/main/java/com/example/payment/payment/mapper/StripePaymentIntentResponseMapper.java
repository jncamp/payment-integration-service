package com.example.payment.payment.mapper;

import com.example.payment.dto.stripe.StripePaymentIntentResponse;
import com.example.payment.entity.ChargeEntity;
import com.example.payment.entity.PaymentIntentEntity;
import com.example.payment.enums.PaymentStatus;
import com.example.payment.shared.error.ApiException;
import com.example.payment.repository.ChargeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;

@Component
public class StripePaymentIntentResponseMapper {

    private final ChargeRepository chargeRepository;
    private final ObjectMapper objectMapper;

    public StripePaymentIntentResponseMapper(ChargeRepository chargeRepository,
                                             ObjectMapper objectMapper) {
        this.chargeRepository = chargeRepository;
        this.objectMapper = objectMapper;
    }

    public StripePaymentIntentResponse toResponse(PaymentIntentEntity payment, boolean replay) {
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

    private ChargeEntity findLatestCharge(PaymentIntentEntity payment) {
        return chargeRepository.findById(payment.getLatestChargeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Charge not found"));
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

    private Map<String, String> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank() || "{}".equals(metadataJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
