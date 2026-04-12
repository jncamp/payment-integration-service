package com.example.payment.dto.stripe;

public class StripeRefundResponse {
    private String id;
    private String object;
    private String paymentIntent;
    private String charge;
    private Long amount;
    private String currency;
    private String reason;
    private String status;
    private Long created;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public String getPaymentIntent() { return paymentIntent; }
    public void setPaymentIntent(String paymentIntent) { this.paymentIntent = paymentIntent; }
    public String getCharge() { return charge; }
    public void setCharge(String charge) { this.charge = charge; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }
}
