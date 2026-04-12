package com.example.payment.dto.stripe;

import java.util.Map;

public class StripePaymentIntentResponse {
    private String id;
    private String object;
    private Long amount;
    private String currency;
    private String status;
    private String clientSecret;
    private String customerName;
    private String customerEmail;
    private Map<String, String> metadata;
    private Long created;
    private String latestCharge;
    private boolean idempotentReplay;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }
    public String getLatestCharge() { return latestCharge; }
    public void setLatestCharge(String latestCharge) { this.latestCharge = latestCharge; }
    public boolean isIdempotentReplay() { return idempotentReplay; }
    public void setIdempotentReplay(boolean idempotentReplay) { this.idempotentReplay = idempotentReplay; }
}
