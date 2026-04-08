package com.example.payment.controller;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundPaymentRequest;
import com.example.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        request.setIdempotencyKey(idempotencyKey);

        PaymentResponse response = paymentService.createPayment(request);

        if (response.isIdempotentReplay()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id) {
        return paymentService.getPayment(id);
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundPaymentRequest request) {
        return paymentService.refundPayment(id, request);
    }
}
