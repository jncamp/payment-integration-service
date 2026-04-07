package com.example.payment.provider;

public class ProviderRefundResult {
    private final String providerRefundId;
    private final boolean success;
    private final String failureReason;

    public ProviderRefundResult(String providerRefundId, boolean success, String failureReason) {
        this.providerRefundId = providerRefundId;
        this.success = success;
        this.failureReason = failureReason;
    }

    public String getProviderRefundId() { return providerRefundId; }
    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
}
