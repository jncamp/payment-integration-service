package com.example.payment.entity;

import com.example.payment.enums.PaymentProvider;
import com.example.payment.enums.WebhookStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private ApiClient client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "provider_event_id", nullable = false, length = 150)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "object_type", length = 80)
    private String objectType;

    @Column(name = "object_id", length = 120)
    private String objectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookStatus status;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "http_headers_json", nullable = false, columnDefinition = "jsonb")
    private String httpHeadersJson = "{}";

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "processing_error")
    private String processingError;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ApiClient getClient() { return client; }
    public void setClient(ApiClient client) { this.client = client; }
    public PaymentProvider getProvider() { return provider; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }
    public String getProviderEventId() { return providerEventId; }
    public void setProviderEventId(String providerEventId) { this.providerEventId = providerEventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public WebhookStatus getStatus() { return status; }
    public void setStatus(WebhookStatus status) { this.status = status; }
    public Boolean getSignatureValid() { return signatureValid; }
    public void setSignatureValid(Boolean signatureValid) { this.signatureValid = signatureValid; }
    public String getHttpHeadersJson() { return httpHeadersJson; }
    public void setHttpHeadersJson(String httpHeadersJson) { this.httpHeadersJson = httpHeadersJson; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}
