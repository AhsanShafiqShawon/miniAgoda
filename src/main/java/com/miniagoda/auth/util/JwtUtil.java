package com.miniagoda.auth.util;

import org.springframework.stereotype.Component;

import com.miniagoda.user.entity.User;

@Component
public class JwtUtil {

    // Read secret and expirations from application.properties
    // using @Value

    // Build a signing key from the secret

    // generateAccessToken(User user)
    public String generateAccessToken(User user) {
        return "Ek Bishal Jotil access token";
    }
    // generateRefreshToken(User user)
    public String generateRefreshToken(User user) {
        return "Ek Bishal Jotil refresh token";
    }
    // extractEmail(String token)
    // isTokenValid(String token)

    // private helper: buildToken(User user, long expiration, Map<String, Object> extraClaims)
    // private helper: extractAllClaims(String token)
}