package com.example.payment.controller;

import com.example.payment.dto.AuthRequest;
import com.example.payment.dto.AuthResponse;
import com.example.payment.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody AuthRequest request) {

        // Demo-only hardcoded authentication
        if (!"admin".equals(request.getUsername())
                || !"password".equals(request.getPassword())) {

            return ResponseEntity.status(401).build();
        }

        String token = jwtService.generateToken(request.getUsername());

        return ResponseEntity.ok(new AuthResponse(token));
    }
}
