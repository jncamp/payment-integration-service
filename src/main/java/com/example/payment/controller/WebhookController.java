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
            @Valid @RequestBody WebhookEventRequest request) {

        if (signature == null || !paymentService.hasValidWebhookSignature(signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        paymentService.processWebhook(request.getProviderPaymentId(), request.getEventType());
        return ResponseEntity.ok(Map.of("message", "Webhook processed"));
    }
}
