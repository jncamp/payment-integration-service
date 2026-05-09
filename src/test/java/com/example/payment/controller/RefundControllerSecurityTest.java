package com.example.payment.controller;

import com.example.payment.config.SecurityConfig;
import com.example.payment.dto.stripe.CreateRefundRequest;
import com.example.payment.dto.stripe.StripeRefundResponse;
import com.example.payment.exception.GlobalExceptionHandler;
import com.example.payment.security.ApiKeyFilter;
import com.example.payment.security.JwtAuthenticationFilter;
import com.example.payment.security.JwtService;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RefundController.class)
@Import({SecurityConfig.class, ApiKeyFilter.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.api-key=test-api-key",
        "jwt.secret=refund-controller-test-secret-that-is-long-enough-123456789",
        "jwt.expiration-ms=86400000"
})
class RefundControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void createRefundRejectsRequestWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/refunds")
                        .contentType("application/json")
                        .content(validRefundRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRefundAcceptsValidApiKey() throws Exception {
        when(paymentService.createRefund(any(CreateRefundRequest.class))).thenReturn(sampleRefund());

        mockMvc.perform(post("/api/refunds")
                        .header("X-API-Key", "test-api-key")
                        .contentType("application/json")
                        .content(validRefundRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("re_test_123"))
                .andExpect(jsonPath("$.paymentIntent").value("pi_test_123"))
                .andExpect(jsonPath("$.status").value("succeeded"));
    }

    @Test
    void createRefundValidatesRequiredPaymentIntentId() throws Exception {
        mockMvc.perform(post("/api/refunds")
                        .header("X-API-Key", "test-api-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 500,
                                  "reason": "requested_by_customer"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.param").value("paymentIntentId"));
    }

    private String validRefundRequest() {
        return """
                {
                  "paymentIntentId": "pi_test_123",
                  "amount": 500,
                  "reason": "requested_by_customer"
                }
                """;
    }

    private StripeRefundResponse sampleRefund() {
        StripeRefundResponse response = new StripeRefundResponse();
        response.setId("re_test_123");
        response.setObject("refund");
        response.setPaymentIntent("pi_test_123");
        response.setCharge("ch_test_123");
        response.setAmount(500L);
        response.setCurrency("usd");
        response.setReason("requested_by_customer");
        response.setStatus("succeeded");
        return response;
    }
}
