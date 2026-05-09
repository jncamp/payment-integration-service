package com.example.payment.controller;

import com.example.payment.config.SecurityConfig;
import com.example.payment.dto.stripe.CreatePaymentIntentRequest;
import com.example.payment.dto.stripe.StripePaymentIntentResponse;
import com.example.payment.exception.GlobalExceptionHandler;
import com.example.payment.security.ApiKeyFilter;
import com.example.payment.security.JwtAuthenticationFilter;
import com.example.payment.security.JwtService;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentIntentController.class)
@Import({SecurityConfig.class, ApiKeyFilter.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.api-key=test-api-key",
        "jwt.secret=payment-intent-test-secret-that-is-long-enough-123456789",
        "jwt.expiration-ms=86400000"
})
class PaymentIntentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PaymentService paymentService;

    @Test
    void protectedCreateRejectsRequestWithoutApiKeyOrJwt() throws Exception {
        mockMvc.perform(post("/api/payment_intents")
                        .contentType("application/json")
                        .content(validCreateRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedCreateAcceptsValidApiKey() throws Exception {
        when(paymentService.createPaymentIntent(any(CreatePaymentIntentRequest.class), eq("idem-123")))
                .thenReturn(samplePaymentIntent(false));

        mockMvc.perform(post("/api/payment_intents")
                        .header("X-API-Key", "test-api-key")
                        .header("Idempotency-Key", "idem-123")
                        .contentType("application/json")
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("pi_test_123"))
                .andExpect(jsonPath("$.status").value("requires_payment_method"));

        ArgumentCaptor<CreatePaymentIntentRequest> requestCaptor = ArgumentCaptor.forClass(CreatePaymentIntentRequest.class);
        verify(paymentService).createPaymentIntent(requestCaptor.capture(), eq("idem-123"));
        assertThat(requestCaptor.getValue().getCustomerEmail()).isEqualTo("john.test@example.com");
    }

    @Test
    void protectedCreateAcceptsValidJwtWithoutApiKey() throws Exception {
        String token = jwtService.generateToken("admin");
        when(paymentService.createPaymentIntent(any(CreatePaymentIntentRequest.class), eq(null)))
                .thenReturn(samplePaymentIntent(false));

        mockMvc.perform(post("/api/payment_intents")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("pi_test_123"));
    }

    @Test
    void protectedCreateReturnsOkForIdempotentReplay() throws Exception {
        when(paymentService.createPaymentIntent(any(CreatePaymentIntentRequest.class), eq("repeat-key")))
                .thenReturn(samplePaymentIntent(true));

        mockMvc.perform(post("/api/payment_intents")
                        .header("X-API-Key", "test-api-key")
                        .header("Idempotency-Key", "repeat-key")
                        .contentType("application/json")
                        .content(validCreateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));
    }

    @Test
    void validationErrorsUseStripeStyleErrorShape() throws Exception {
        mockMvc.perform(post("/api/payment_intents")
                        .header("X-API-Key", "test-api-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "amount": 0,
                                  "currency": "usd"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("parameter_invalid"));
    }

    private String validCreateRequest() {
        return """
                {
                  "amount": 1000,
                  "currency": "usd",
                  "customerName": "John Test",
                  "customerEmail": "john.test@example.com",
                  "metadata": {
                    "orderId": "ORD-1001"
                  }
                }
                """;
    }

    private StripePaymentIntentResponse samplePaymentIntent(boolean idempotentReplay) {
        StripePaymentIntentResponse response = new StripePaymentIntentResponse();
        response.setId("pi_test_123");
        response.setObject("payment_intent");
        response.setAmount(1000L);
        response.setCurrency("usd");
        response.setStatus("requires_payment_method");
        response.setClientSecret("pi_test_123_secret_test");
        response.setCustomerName("John Test");
        response.setCustomerEmail("john.test@example.com");
        response.setIdempotentReplay(idempotentReplay);
        return response;
    }
}
