package com.example.payment.controller;

import com.example.payment.config.SecurityConfig;
import com.example.payment.security.ApiKeyFilter;
import com.example.payment.security.JwtAuthenticationFilter;
import com.example.payment.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ApiKeyFilter.class, JwtAuthenticationFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "app.security.api-key=test-api-key",
        "jwt.secret=auth-controller-test-secret-that-is-long-enough-123456789",
        "jwt.expiration-ms=86400000"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWithValidCredentialsReturnsJwtToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankOrNullString())));
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
