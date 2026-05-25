package com.miniagoda.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miniagoda.auth.dto.RegisterRequest;
import com.miniagoda.auth.dto.RegisterResponse;
import com.miniagoda.auth.service.AuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse httpServletResponse) {
        RegisterResponse registerResponse = authService.register(request, httpServletResponse);
        return ResponseEntity.ok(registerResponse);
    }
}