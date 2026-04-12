package com.example.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;

public class WebhookEventRequest {

    @NotBlank(message = "providerPaymentId is required")
    private String providerPaymentId;

    @NotBlank(message = "eventType is required")
    private String eventType;

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public static WebhookEventRequest fromJson(String rawBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(rawBody);
            WebhookEventRequest request = new WebhookEventRequest();

            if (root.hasNonNull("providerPaymentId")) {
                request.setProviderPaymentId(root.get("providerPaymentId").asText());
            }
            if (root.hasNonNull("eventType")) {
                request.setEventType(root.get("eventType").asText());
            }
            if (request.getEventType() == null && root.hasNonNull("type")) {
                request.setEventType(root.get("type").asText());
            }
            if (request.getProviderPaymentId() == null) {
                JsonNode nested = root.path("data").path("object").path("id");
                if (!nested.isMissingNode() && !nested.isNull()) {
                    request.setProviderPaymentId(nested.asText());
                }
            }
            return request;
        } catch (Exception e) {
            throw new RuntimeException("Invalid webhook payload", e);
        }
    }
}
