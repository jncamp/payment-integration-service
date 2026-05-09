package com.example.payment.payment.controller;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.RefundPaymentRequest;
import com.example.payment.payment.service.PaymentService;
import com.example.payment.refund.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Hidden;
@Tag(name = "Legacy Payments", description = "Original payment endpoints using internal UUID payment resources.")
@RestController
@RequestMapping("/api/payments")
@Hidden
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
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
        return refundService.refundPayment(id, request);
    }
}
