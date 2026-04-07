package com.example.payment.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;

public class WebhookEventRequest {

    @NotBlank(message = "providerPaymentId is required")
    private String providerPaymentId;

    @NotBlank(message = "eventType is required")
    private String eventType;

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public static WebhookEventRequest fromJson(String rawBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(rawBody, WebhookEventRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid webhook payload", e);
        }
    }
}