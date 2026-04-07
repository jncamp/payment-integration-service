package com.example.payment.service;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundPaymentRequest;
import com.example.payment.entity.PaymentTransaction;
import com.example.payment.enums.PaymentProvider;
import com.example.payment.enums.PaymentStatus;
import com.example.payment.exception.ApiException;
import com.example.payment.provider.PaymentGatewayClient;
import com.example.payment.provider.ProviderChargeResult;
import com.example.payment.provider.ProviderRefundResult;
import com.example.payment.repository.PaymentTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentTransactionRepository repository;
    private final PaymentGatewayClient paymentGatewayClient;

    public PaymentService(PaymentTransactionRepository repository, PaymentGatewayClient paymentGatewayClient) {
        this.repository = repository;
        this.paymentGatewayClient = paymentGatewayClient;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<PaymentTransaction> existing =
                    repository.findByIdempotencyKey(request.getIdempotencyKey());

            if (existing.isPresent()) {
                return PaymentResponse.from(existing.get());
            }
        }

        PaymentTransaction payment = new PaymentTransaction();
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerEmail(request.getCustomerEmail());
        payment.setCustomerName(request.getCustomerName());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.MOCKPAY);
        repository.save(payment);

        ProviderChargeResult result = paymentGatewayClient.charge(request);
        payment.setProviderPaymentId(result.getProviderPaymentId());
        if (result.isSuccess()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setFailureReason(null);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.getFailureReason());
        }

        return PaymentResponse.from(repository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        return PaymentResponse.from(findPayment(id));
    }

    @Transactional
    public PaymentResponse refundPayment(UUID id, RefundPaymentRequest request) {
        PaymentTransaction payment = findPayment(id);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only SUCCEEDED payments can be refunded");
        }

        ProviderRefundResult result = paymentGatewayClient.refund(payment, request.getReason());
        if (!result.isSuccess()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, result.getFailureReason());
        }

        payment.setProviderRefundId(result.getProviderRefundId());
        payment.setStatus(PaymentStatus.REFUNDED);
        return PaymentResponse.from(repository.save(payment));
    }

    @Transactional
    public void processWebhook(String providerPaymentId, String eventType) {
        PaymentTransaction payment = repository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found for provider payment id"));

        switch (eventType) {
            case "payment.succeeded" -> {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setFailureReason(null);
            }
            case "payment.failed" -> payment.setStatus(PaymentStatus.FAILED);
            case "payment.refunded" -> payment.setStatus(PaymentStatus.REFUNDED);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported eventType: " + eventType);
        }

        repository.save(payment);
    }

    public boolean hasValidWebhookSignature(String signature, String rawBody) {
        return paymentGatewayClient.isValidWebhookSignature(signature, rawBody);
    }

    private PaymentTransaction findPayment(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }
}
