package com.example.payment.refund.controller;

import com.example.payment.dto.stripe.CreateRefundRequest;
import com.example.payment.dto.stripe.StripeRefundResponse;
import com.example.payment.refund.service.RefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Refunds", description = "Create partial or full refunds against PaymentIntents.")
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<StripeRefundResponse> createRefund(@Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(refundService.createRefund(request));
    }
}
