package com.miniagoda.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.miniagoda.auth.dto.RegisterRequest;
import com.miniagoda.auth.dto.RegisterResponse;
import com.miniagoda.auth.exception.EmailAlreadyExistException;
import com.miniagoda.auth.util.JwtUtil;
import com.miniagoda.user.entity.Role;
import com.miniagoda.user.entity.User;
import com.miniagoda.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterResponse register(RegisterRequest request, HttpServletResponse response) {
        if(userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistException();
        }
        
        User user = new User();

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);

        User savedUser = userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(savedUser);
        String refreshToken = jwtUtil.generateRefreshToken(savedUser);

        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        
        response.addCookie(cookie);

        return new RegisterResponse(accessToken);
    }
}