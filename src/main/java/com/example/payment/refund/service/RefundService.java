package com.example.payment.refund.service;

import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundPaymentRequest;
import com.example.payment.dto.stripe.CreateRefundRequest;
import com.example.payment.dto.stripe.StripeRefundResponse;
import com.example.payment.entity.ChargeEntity;
import com.example.payment.entity.PaymentIntentEntity;
import com.example.payment.entity.RefundEntity;
import com.example.payment.enums.ChargeStatus;
import com.example.payment.enums.PaymentStatus;
import com.example.payment.enums.RefundStatus;
import com.example.payment.shared.error.ApiException;
import com.example.payment.provider.PaymentProviderGateway;
import com.example.payment.repository.ChargeRepository;
import com.example.payment.repository.PaymentIntentRepository;
import com.example.payment.repository.RefundRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class RefundService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final ChargeRepository chargeRepository;
    private final RefundRepository refundRepository;
    private final PaymentProviderGateway paymentProviderGateway;

    public RefundService(PaymentIntentRepository paymentIntentRepository,
                         ChargeRepository chargeRepository,
                         RefundRepository refundRepository,
                         PaymentProviderGateway paymentProviderGateway) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.chargeRepository = chargeRepository;
        this.refundRepository = refundRepository;
        this.paymentProviderGateway = paymentProviderGateway;
    }

    @Transactional
    public PaymentResponse refundPayment(UUID id, RefundPaymentRequest request) {
        PaymentIntentEntity payment = findPayment(id);
        CreateRefundRequest refundRequest = new CreateRefundRequest();
        refundRequest.setPaymentIntentId(payment.getProviderPaymentIntentId());
        refundRequest.setReason(request.getReason());
        StripeRefundResponse refundResponse = createRefund(refundRequest);
        payment = findPayment(id);
        return PaymentResponse.from(payment, refundResponse.getId());
    }

    @Transactional
    public StripeRefundResponse createRefund(CreateRefundRequest request) {
        PaymentIntentEntity payment = findByProviderPaymentId(request.getPaymentIntentId());
        if (payment.getStatus() != PaymentStatus.SUCCEEDED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only succeeded or partially refunded payment intents can be refunded");
        }

        try {
            ChargeEntity charge = findLatestCharge(payment);
            long alreadyRefunded = charge.getAmountRefunded() == null ? 0L : charge.getAmountRefunded();
            long remainingRefundable = payment.getAmount() - alreadyRefunded;
            if (remainingRefundable <= 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Payment is already fully refunded");
            }

            long refundAmount = request.getAmount() != null ? request.getAmount() : remainingRefundable;
            if (refundAmount <= 0 || refundAmount > remainingRefundable) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Refund amount must be between 1 and remaining refundable amount");
            }

            Refund stripeRefund = paymentProviderGateway.createRefund(
                    charge.getProviderChargeId(),
                    payment.getProviderPaymentIntentId(),
                    refundAmount,
                    request.getReason()
            );

            OffsetDateTime now = OffsetDateTime.now();

            RefundEntity refund = new RefundEntity();
            refund.setPaymentIntent(payment);
            refund.setCharge(charge);
            refund.setInternalReference("REF-" + randomCompactId().substring(0, 18).toUpperCase());
            refund.setProviderRefundId(stripeRefund.getId());
            refund.setAmount(stripeRefund.getAmount());
            refund.setCurrency(stripeRefund.getCurrency().toUpperCase());
            refund.setReason(stripeRefund.getReason());
            refund.setStatus(RefundStatus.SUCCEEDED);
            refund.setSucceededAt(now);
            refund.setCreatedAt(now);
            refund.setUpdatedAt(now);
            refund.setMetadataJson("{}");
            refund = refundRepository.save(refund);

            long newRefundedTotal = alreadyRefunded + stripeRefund.getAmount();
            charge.setAmountRefunded(newRefundedTotal);
            charge.setStatus(newRefundedTotal >= charge.getAmountCaptured() ? ChargeStatus.REFUNDED : ChargeStatus.PARTIALLY_REFUNDED);
            chargeRepository.save(charge);

            payment.setStatus(newRefundedTotal >= payment.getAmount() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            payment.setUpdatedAt(now);
            paymentIntentRepository.save(payment);

            StripeRefundResponse response = new StripeRefundResponse();
            response.setId(refund.getProviderRefundId());
            response.setObject("refund");
            response.setPaymentIntent(payment.getProviderPaymentIntentId());
            response.setCharge(charge.getProviderChargeId());
            response.setAmount(refund.getAmount());
            response.setCurrency(refund.getCurrency().toLowerCase());
            response.setReason(refund.getReason());
            response.setStatus(refund.getStatus().name().toLowerCase());
            response.setCreated(refund.getCreatedAt().toEpochSecond());
            return response;
        } catch (StripeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stripe refund failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public String latestRefundId(PaymentIntentEntity payment) {
        return refundRepository.findTopByPaymentIntentOrderByCreatedAtDesc(payment)
                .map(RefundEntity::getProviderRefundId)
                .orElse(null);
    }

    private PaymentIntentEntity findPayment(UUID id) {
        return paymentIntentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private PaymentIntentEntity findByProviderPaymentId(String providerPaymentId) {
        return paymentIntentRepository.findByProviderPaymentIntentId(providerPaymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment intent not found"));
    }

    private ChargeEntity findLatestCharge(PaymentIntentEntity payment) {
        if (payment.getLatestChargeId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "No charge found for payment intent");
        }
        return chargeRepository.findById(payment.getLatestChargeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Charge not found"));
    }

    private String randomCompactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
