package com.example.payment.controller;

import com.example.payment.dto.stripe.CreateRefundRequest;
import com.example.payment.dto.stripe.StripeRefundResponse;
import com.example.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final PaymentService paymentService;

    public RefundController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<StripeRefundResponse> createRefund(@Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createRefund(request));
    }
}
