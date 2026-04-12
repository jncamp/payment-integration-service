package com.example.payment.dto.stripe;

public class StripeEventResponse {
    private String id;
    private String object;
    private String type;
    private Long created;
    private EventData data;

    public static class EventData {
        private StripePaymentIntentResponse object;

        public StripePaymentIntentResponse getObject() { return object; }
        public void setObject(StripePaymentIntentResponse object) { this.object = object; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }
    public EventData getData() { return data; }
    public void setData(EventData data) { this.data = data; }
}
