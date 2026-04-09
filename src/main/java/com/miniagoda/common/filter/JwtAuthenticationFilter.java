package com.miniagoda.common.filter;

import com.miniagoda.common.security.PublicRoutes;
import com.miniagoda.common.util.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final SecurityErrorWriter securityErrorWriter;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder, SecurityErrorWriter securityErrorWriter) {
        this.jwtDecoder = jwtDecoder;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        for (String[] matcher : PublicRoutes.MATCHERS) {
            if (method.equals(matcher[0]) && path.startsWith(matcher[1].replace("/**", ""))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, request, "Missing or malformed Authorization header.");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            writeUnauthorized(response, request, "Invalid or expired token.");
            return;
        }

        String userId = jwt.getSubject();
        String role = jwt.getClaimAsString("role");

        if (role == null) {
            writeUnauthorized(response, request, "Token is missing required claims.");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response,
                               HttpServletRequest request,
                               String message) throws IOException {
    securityErrorWriter.write(response, request, 401, "Unauthorized", message);
    }
}