package com.miniagoda.auth.security;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.miniagoda.auth.util.JwtUtil;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserDetailsService userDetailsService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil, userDetailsService, redisTemplate);
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UserDetails userDetailsWith(String username) {
        UserDetails ud = mock(UserDetails.class);
        // getUsername() is not stubbed here — the filter reads the email from
        // jwtUtil.extractEmail(), not from UserDetails. Stubbing it would trigger
        // UnnecessaryStubbingException in tests that don't call getUsername().
        when(ud.getAuthorities()).thenReturn(java.util.Collections.emptyList());
        return ud;
    }

    // -------------------------------------------------------------------------
    // Missing / malformed Authorization header
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Missing or invalid Authorization header")
    class NoAuthHeader {

        @Test
        @DisplayName("passes through when Authorization header is absent")
        void doFilter_noAuthHeader_continuesChain() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(jwtUtil, never()).extractEmail(anyString());
        }

        @Test
        @DisplayName("passes through when Authorization header does not start with 'Bearer '")
        void doFilter_nonBearerHeader_continuesChain() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(jwtUtil, never()).extractEmail(anyString());
        }

        @Test
        @DisplayName("does not set authentication when header is missing")
        void doFilter_noAuthHeader_noAuthenticationSet() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Happy path — valid token, not blocklisted
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Valid token — authentication succeeds")
    class ValidToken {

        @Test
        @DisplayName("sets authentication in the SecurityContext for a valid token")
        void doFilter_validToken_setsAuthentication() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer valid.token.here");
            when(jwtUtil.extractEmail("valid.token.here")).thenReturn("alice@example.com");
            when(jwtUtil.extractJti("valid.token.here")).thenReturn("jti-123");
            when(redisTemplate.hasKey("blocklist:jti-123")).thenReturn(false);
            UserDetails ud = userDetailsWith("alice@example.com");
            when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(ud);
            when(jwtUtil.isTokenValid("valid.token.here", ud)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.isAuthenticated()).isTrue();
            assertThat(auth.getPrincipal()).isEqualTo(ud);
        }

        @Test
        @DisplayName("continues the filter chain after setting authentication")
        void doFilter_validToken_continuesChain() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer valid.token.here");
            when(jwtUtil.extractEmail("valid.token.here")).thenReturn("alice@example.com");
            when(jwtUtil.extractJti("valid.token.here")).thenReturn("jti-123");
            when(redisTemplate.hasKey("blocklist:jti-123")).thenReturn(false);
            UserDetails ud = userDetailsWith("alice@example.com");
            when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(ud);
            when(jwtUtil.isTokenValid("valid.token.here", ud)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("checks Redis blocklist using the correct key format 'blocklist:<jti>'")
        void doFilter_validToken_checksCorrectBlocklistKey() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer valid.token.here");
            when(jwtUtil.extractEmail("valid.token.here")).thenReturn("alice@example.com");
            when(jwtUtil.extractJti("valid.token.here")).thenReturn("abc-uuid");
            when(redisTemplate.hasKey("blocklist:abc-uuid")).thenReturn(false);
            UserDetails ud = userDetailsWith("alice@example.com");
            when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(ud);
            when(jwtUtil.isTokenValid("valid.token.here", ud)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(redisTemplate).hasKey("blocklist:abc-uuid");
        }

        @Test
        @DisplayName("does not load user or set auth when token is invalid")
        void doFilter_invalidToken_doesNotSetAuthentication() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
            when(jwtUtil.extractEmail("bad.token")).thenReturn("alice@example.com");
            when(jwtUtil.extractJti("bad.token")).thenReturn("jti-999");
            when(redisTemplate.hasKey("blocklist:jti-999")).thenReturn(false);
            // Plain mock — getAuthorities() is never called when isTokenValid() returns false,
            // so userDetailsWith() would leave an unnecessary stub.
            UserDetails ud = mock(UserDetails.class);
            when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(ud);
            when(jwtUtil.isTokenValid("bad.token", ud)).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    // -------------------------------------------------------------------------
    // Blocklisted token
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Blocklisted token")
    class BlocklistedToken {

        @Test
        @DisplayName("returns 401 when token jti is on the blocklist")
        void doFilter_blocklistedToken_returns401() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer blocklisted.token");
            when(jwtUtil.extractEmail("blocklisted.token")).thenReturn("alice@example.com");
            when(jwtUtil.extractJti("blocklisted.token")).thenReturn("revoked-jti");
            when(redisTemplate.hasKey("blocklist:revoked-jti")).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalidated");
        }

        @Test
        @DisplayName("does not continue the filter chain for a blocklisted token")
        void doFilter_blocklistedToken_doesNotContinueChain() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer blocklisted.token");
            when(jwtUtil.extractEmail("blocklisted.token")).thenReturn("alice@example.com");
            when(jwtUtil.extractJti("blocklisted.token")).thenReturn("revoked-jti");
            when(redisTemplate.hasKey("blocklist:revoked-jti")).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("does not set authentication for a blocklisted token")
        void doFilter_blocklistedToken_noAuthenticationSet() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer blocklisted.token");
            when(jwtUtil.extractEmail("blocklisted.token")).thenReturn("alice@example.com");
            when(jwtUtil.extractJti("blocklisted.token")).thenReturn("revoked-jti");
            when(redisTemplate.hasKey("blocklist:revoked-jti")).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(userDetailsService, never()).loadUserByUsername(anyString());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Already-authenticated context
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Already-authenticated SecurityContext")
    class AlreadyAuthenticated {

        @Test
        @DisplayName("skips processing when context already has authentication")
        void doFilter_alreadyAuthenticated_skipsUserLoad() throws Exception {
            // Pre-populate the SecurityContext with an existing authentication
            Authentication existingAuth = mock(Authentication.class);
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(existingAuth);
            SecurityContextHolder.setContext(ctx);

            when(request.getHeader("Authorization")).thenReturn("Bearer some.token");
            when(jwtUtil.extractEmail("some.token")).thenReturn("alice@example.com");

            filter.doFilterInternal(request, response, filterChain);

            verify(userDetailsService, never()).loadUserByUsername(anyString());
            verify(filterChain).doFilter(request, response);
        }
    }

    // -------------------------------------------------------------------------
    // JwtException handling
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("JwtException handling")
    class JwtExceptionHandling {

        @Test
        @DisplayName("returns 401 status when JwtException is thrown")
        void doFilter_jwtException_returns401() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
            when(jwtUtil.extractEmail("bad.token")).thenThrow(new JwtException("invalid"));
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

            filter.doFilterInternal(request, response, filterChain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("writes JSON error body when JwtException is thrown")
        void doFilter_jwtException_writesJsonErrorBody() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
            when(jwtUtil.extractEmail("bad.token")).thenThrow(new JwtException("invalid"));

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            filter.doFilterInternal(request, response, filterChain);

            assertThat(sw.toString()).contains("error");
            assertThat(sw.toString()).contains("Invalid or expired token");
        }

        @Test
        @DisplayName("sets Content-Type to application/json when JwtException is thrown")
        void doFilter_jwtException_setsJsonContentType() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
            when(jwtUtil.extractEmail("bad.token")).thenThrow(new JwtException("invalid"));

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            filter.doFilterInternal(request, response, filterChain);

            verify(response).setContentType("application/json");
        }

        @Test
        @DisplayName("does not continue the filter chain when JwtException is thrown")
        void doFilter_jwtException_doesNotContinueChain() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
            when(jwtUtil.extractEmail("bad.token")).thenThrow(new JwtException("invalid"));

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("does not set authentication when JwtException is thrown")
        void doFilter_jwtException_noAuthenticationSet() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
            when(jwtUtil.extractEmail("bad.token")).thenThrow(new JwtException("invalid"));

            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}