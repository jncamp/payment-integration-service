package com.example.payment.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "unit-test-jwt-secret-that-is-long-enough-123456789");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 86_400_000L);
        jwtService.init();
    }

    @Test
    void generateTokenCreatesValidTokenContainingUsername() {
        String token = jwtService.generateToken("admin");

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void invalidTokenIsRejected() {
        assertThat(jwtService.isTokenValid("not-a-real-token")).isFalse();
    }
}
