package com.miniagoda.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.miniagoda.auth.dto.LoginRequest;
import com.miniagoda.auth.dto.LoginResponse;
import com.miniagoda.auth.dto.RegisterRequest;
import com.miniagoda.auth.dto.RegisterResponse;
import com.miniagoda.auth.entity.RefreshToken;
import com.miniagoda.auth.exception.EmailAlreadyExistException;
import com.miniagoda.auth.exception.InvalidRefreshTokenException;
import com.miniagoda.auth.repository.RefreshTokenRepository;
import com.miniagoda.auth.security.UserDetailsImpl;
import com.miniagoda.auth.util.JwtUtil;
import com.miniagoda.notification.event.AccountRegisteredEvent;
import com.miniagoda.notification.event.AccountRegisteredNotificationEvent;
import com.miniagoda.user.entity.Role;
import com.miniagoda.user.entity.User;
import com.miniagoda.user.repository.UserRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    public AuthService(
        UserRepository userRepository,
        JwtUtil jwtUtil,
        PasswordEncoder passwordEncoder,
        RefreshTokenRepository refreshTokenRepository,
        RedisTemplate<String, String> redisTemplate,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.redisTemplate = redisTemplate;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
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

        AccountRegisteredEvent accountRegisteredEvent = new AccountRegisteredEvent(
            savedUser.getEmail(),
            savedUser.getFirstName() + " " + user.getLastName(),
            null
        );

        applicationEventPublisher.publishEvent(new AccountRegisteredNotificationEvent(this, accountRegisteredEvent));

        String accessToken = jwtUtil.generateAccessToken(savedUser);
        
        handleRefreshToken(savedUser, response);

        return new RegisterResponse(accessToken);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        
        handleRefreshToken(user, response);
        
        return new LoginResponse(accessToken);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();

        if(cookies == null) return;

        for(Cookie cookie : cookies) {
            if(cookie.getName().equals("refreshToken")) {
                refreshTokenRepository.deleteByTokenHash(hashToken(cookie.getValue()));

                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);

                    try {
                        String jti = jwtUtil.extractJti(token);
                        Date expiration = jwtUtil.extractExpiration(token);
                        long ttl = expiration.getTime() - System.currentTimeMillis();
                        
                        if(ttl > 0) {
                            redisTemplate.opsForValue().set(
                                "blocklist:" + jti,
                                "1",
                                ttl,
                                TimeUnit.MILLISECONDS
                            );
                        }
                    }
                    catch(JwtException e) {}
                }
                
                Cookie c = new Cookie("refreshToken", "");
                c.setHttpOnly(true);
                c.setSecure(true);
                c.setPath("/auth/refresh");
                c.setMaxAge(0);
                response.addCookie(c);
            }
        }
    }

    @Transactional
    public RegisterResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        String accessToken = null;

        if(cookies == null) throw new InvalidRefreshTokenException();

        for(Cookie cookie : cookies) {
            if(cookie.getName().equals("refreshToken")) {
                RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(hashToken(cookie.getValue()))
                .orElseThrow(() -> new InvalidRefreshTokenException());

                if(
                    refreshToken.isRevoked() || 
                    refreshToken.getExpiresAt().isBefore(LocalDateTime.now()) || 
                    !jwtUtil.isTokenValid(cookie.getValue(), new UserDetailsImpl(refreshToken.getUser()))
                ) throw new InvalidRefreshTokenException();

                refreshToken.setRevoked(true);

                accessToken = jwtUtil.generateAccessToken(refreshToken.getUser());
                handleRefreshToken(refreshToken.getUser(), response);
            }
        }
        if(accessToken == null) throw new InvalidRefreshTokenException();
        return new RegisterResponse(accessToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void handleRefreshToken(User user, HttpServletResponse response) {
        String refreshToken = jwtUtil.generateRefreshToken(user);

        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setTokenHash(hashToken(refreshToken));
        refreshTokenEntity.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        refreshTokenEntity.setUser(user);

        refreshTokenRepository.revokeAllByUser(user);
        refreshTokenRepository.save(refreshTokenEntity);

        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge((int) (refreshTokenExpiration / 1000));

        response.addCookie(cookie);
    }
}