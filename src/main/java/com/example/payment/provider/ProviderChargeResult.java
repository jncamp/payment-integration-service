package com.example.payment.provider;

public class ProviderChargeResult {
    private final String providerPaymentId;
    private final boolean success;
    private final String failureReason;

    public ProviderChargeResult(String providerPaymentId, boolean success, String failureReason) {
        this.providerPaymentId = providerPaymentId;
        this.success = success;
        this.failureReason = failureReason;
    }

    public String getProviderPaymentId() { return providerPaymentId; }
    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
}
