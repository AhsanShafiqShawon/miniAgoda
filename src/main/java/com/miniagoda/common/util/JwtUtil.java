package com.miniagoda.common.util;

import com.miniagoda.common.config.JwtConfig;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JwtUtil {

    private final JwtEncoder jwtEncoder;
    private final JwtConfig jwtConfig;

    public JwtUtil(JwtEncoder jwtEncoder, JwtConfig jwtConfig) {
        this.jwtEncoder = jwtEncoder;
        this.jwtConfig = jwtConfig;
    }

    public String generateAccessToken(String userId, String role) {
        return buildToken(userId, role, jwtConfig.getAccessTokenExpiryMs());
    }

    public String generateRefreshToken(String userId, String role) {
        return buildToken(userId, role, jwtConfig.getRefreshTokenExpiryMs());
    }

    private String buildToken(String userId, String role, long expiryMs) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expiryMs);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("miniagoda")
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(userId)
                .claim("role", role)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}