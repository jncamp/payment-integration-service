package com.example.payment.webhook.controller;

import com.example.payment.webhook.service.WebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
@Tag(name = "Stripe Webhooks", description = "Receive and validate Stripe webhook callbacks.")
@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private final WebhookService webhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload) {

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        switch (event.getType()) {

            case "payment_intent.succeeded":
                webhookService.handleStripePaymentIntentSucceeded(event);
                break;

            case "payment_intent.payment_failed":
                webhookService.handleStripePaymentIntentFailed(event);
                break;

            default:
                break;
        }

        return ResponseEntity.ok("Received");
    }
}