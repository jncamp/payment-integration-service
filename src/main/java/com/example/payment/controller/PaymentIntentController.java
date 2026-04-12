package com.example.payment.controller;

import com.example.payment.dto.stripe.*;
import com.example.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment_intents")
public class PaymentIntentController {

    private final PaymentService paymentService;

    public PaymentIntentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<StripePaymentIntentResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        StripePaymentIntentResponse response = paymentService.createPaymentIntent(request, idempotencyKey);
        return response.isIdempotentReplay()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/confirm")
    public StripePaymentIntentResponse confirm(
            @PathVariable("id") String id,
            @RequestBody(required = false) ConfirmPaymentIntentRequest request) {
        if (request == null) {
            request = new ConfirmPaymentIntentRequest();
        }
        return paymentService.confirmPaymentIntent(id, request);
    }

    @GetMapping("/{id}")
    public StripePaymentIntentResponse get(@PathVariable("id") String id) {
        return paymentService.getPaymentIntent(id);
    }
}
