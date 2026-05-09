package com.example.payment.provider;

import com.example.payment.shared.error.ApiException;
import com.stripe.param.RefundCreateParams;
import org.springframework.http.HttpStatus;

public final class StripeRefundReasonMapper {

    private StripeRefundReasonMapper() {
    }

    public static RefundCreateParams.Reason toStripeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return switch (reason) {
            case "duplicate" -> RefundCreateParams.Reason.DUPLICATE;
            case "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT;
            case "requested_by_customer" -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported refund reason. Allowed values: duplicate, fraudulent, requested_by_customer"
            );
        };
    }
}
