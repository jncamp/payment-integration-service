package com.example.payment.controller;

import com.example.payment.dto.stripe.ConfirmPaymentIntentRequest;
import com.example.payment.dto.stripe.CreatePaymentIntentRequest;
import com.example.payment.dto.stripe.CreateRefundRequest;
import com.example.payment.dto.stripe.StripePaymentIntentResponse;
import com.example.payment.dto.stripe.StripeRefundResponse;
import com.example.payment.payment.service.PaymentService;
import com.example.payment.refund.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.tags.Tag;
/**
 * Public demo endpoints used by the portfolio landing page.
 *
 * The production-style REST API under /api remains protected by X-API-Key.
 * These /demo/api endpoints let a potential client click-test the deployed app
 * from the browser without needing Postman or a private API key.
 */
@Tag(name = "Public Demo UI", description = "Unauthenticated browser demo endpoints used by the portfolio landing page.")
@RestController
@RequestMapping("/demo/api")
public class DemoUiApiController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public DemoUiApiController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of(
                "status", "UP",
                "message", "Public portfolio demo endpoint is running"
        );
    }

    @PostMapping("/payment-intents")
    public ResponseEntity<StripePaymentIntentResponse> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        String idempotencyKey = "demo-ui-" + UUID.randomUUID();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paymentService.createPaymentIntent(request, idempotencyKey));
    }

    @PostMapping("/payment-intents/{id}/confirm")
    public StripePaymentIntentResponse confirmPaymentIntent(@PathVariable("id") String id) {
        ConfirmPaymentIntentRequest request = new ConfirmPaymentIntentRequest();
        request.setPaymentMethodId("pm_card_visa");
        return paymentService.confirmPaymentIntent(id, request);
    }

    @GetMapping("/payment-intents/{id}")
    public StripePaymentIntentResponse getPaymentIntent(@PathVariable("id") String id) {
        return paymentService.getPaymentIntent(id);
    }

    @PostMapping("/refunds")
    public ResponseEntity<StripeRefundResponse> createRefund(@Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(refundService.createRefund(request));
    }
}
