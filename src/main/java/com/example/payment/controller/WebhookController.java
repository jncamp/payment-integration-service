package com.example.payment.controller;

import com.example.payment.dto.stripe.StripeEventResponse;
import com.example.payment.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    @PostMapping("/payment-provider")
    public ResponseEntity<StripeEventResponse> handleWebhook() {
        throw new ApiException(HttpStatus.GONE, "Generic payment-provider webhook is disabled. Use /api/webhooks/stripe only.");
    }
}
