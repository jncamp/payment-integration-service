package com.example.payment.controller;

import com.example.payment.dto.WebhookEventRequest;
import com.example.payment.exception.ApiException;
import com.example.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/payment-provider")
public class WebhookController {

    private final PaymentService paymentService;

    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        if (signature == null || !paymentService.hasValidWebhookSignature(signature, rawBody)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        WebhookEventRequest request = WebhookEventRequest.fromJson(rawBody);

        paymentService.processWebhook(request.getProviderPaymentId(), request.getEventType());
        return ResponseEntity.ok(Map.of("message", "Webhook processed"));
    }}
