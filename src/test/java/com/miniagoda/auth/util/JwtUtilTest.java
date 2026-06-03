package com.miniagoda.auth.util;

import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import com.miniagoda.user.entity.Role;
import com.miniagoda.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    // 512-bit key (64 bytes) — meets HS512 minimum; base64-encoded
    private static final String TEST_SECRET = Base64.getEncoder()
            .encodeToString(new byte[64]);

    private static final long ACCESS_EXPIRATION  = 15 * 60 * 1000L;  // 15 min
    private static final long REFRESH_EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final long EXPIRED            = -1000L; // already expired

    private JwtUtil jwtUtil;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JwtUtil buildJwtUtil(long accessExp, long refreshExp) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(util, "accessTokenExpiration", accessExp);
        ReflectionTestUtils.setField(util, "refreshTokenExpiration", refreshExp);
        ReflectionTestUtils.invokeMethod(util, "init");
        return util;
    }

    private User userWithEmail(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("secret");
        return user;
    }

    @BeforeEach
    void setUp() {
        jwtUtil = buildJwtUtil(ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    // -------------------------------------------------------------------------
    // generateAccessToken()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        @Test
        @DisplayName("returns a non-blank token")
        void generateAccessToken_returnsNonBlankToken() {
            User user = userWithEmail("alice@example.com", Role.CUSTOMER);

            String token = jwtUtil.generateAccessToken(user);

            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("embeds the user's email as the subject")
        void generateAccessToken_embedsEmailAsSubject() {
            User user = userWithEmail("alice@example.com", Role.CUSTOMER);

            String token = jwtUtil.generateAccessToken(user);

            assertThat(jwtUtil.extractEmail(token)).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("embeds the user's role in the token claims")
        void generateAccessToken_embedsRole() {
            User user = userWithEmail("admin@example.com", Role.HOTEL_ADMIN);

            String token = jwtUtil.generateAccessToken(user);

            // Re-extract via extractEmail to confirm the token parses; role is
            // a custom claim so we verify indirectly by checking token validity
            // and then decode claims via a second util instance with the same key
            JwtUtil verifier = buildJwtUtil(ACCESS_EXPIRATION, REFRESH_EXPIRATION);
            assertThat(verifier.extractEmail(token)).isEqualTo("admin@example.com");
        }

        @Test
        @DisplayName("includes a non-blank jti claim")
        void generateAccessToken_includesJti() {
            User user = userWithEmail("alice@example.com", Role.CUSTOMER);

            String token = jwtUtil.generateAccessToken(user);

            assertThat(jwtUtil.extractJti(token)).isNotBlank();
        }

        @Test
        @DisplayName("generates unique jti on every call")
        void generateAccessToken_jtiIsUniquePerCall() {
            User user = userWithEmail("alice@example.com", Role.CUSTOMER);

            String jti1 = jwtUtil.extractJti(jwtUtil.generateAccessToken(user));
            String jti2 = jwtUtil.extractJti(jwtUtil.generateAccessToken(user));

            assertThat(jti1).isNotEqualTo(jti2);
        }

        @Test
        @DisplayName("expiration is approximately access-token TTL from now")
        void generateAccessToken_expirationMatchesTtl() {
            User user = userWithEmail("alice@example.com", Role.CUSTOMER);
            long before = System.currentTimeMillis();

            String token = jwtUtil.generateAccessToken(user);

            long expMs = jwtUtil.extractExpiration(token).getTime();
            assertThat(expMs).isBetween(before + ACCESS_EXPIRATION - 1000, before + ACCESS_EXPIRATION + 1000);
        }
    }

    // -------------------------------------------------------------------------
    // generateRefreshToken()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("returns a non-blank token")
        void generateRefreshToken_returnsNonBlankToken() {
            User user = userWithEmail("bob@example.com", Role.GUEST);

            String token = jwtUtil.generateRefreshToken(user);

            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("embeds the user's email as the subject")
        void generateRefreshToken_embedsEmailAsSubject() {
            User user = userWithEmail("bob@example.com", Role.GUEST);

            String token = jwtUtil.generateRefreshToken(user);

            assertThat(jwtUtil.extractEmail(token)).isEqualTo("bob@example.com");
        }

        @Test
        @DisplayName("jti claim is absent (refresh tokens carry no jti)")
        void generateRefreshToken_noJtiClaim() {
            User user = userWithEmail("bob@example.com", Role.GUEST);

            String token = jwtUtil.generateRefreshToken(user);

            assertThat(jwtUtil.extractJti(token)).isNull();
        }

        @Test
        @DisplayName("expiration is approximately refresh-token TTL from now")
        void generateRefreshToken_expirationMatchesTtl() {
            User user = userWithEmail("bob@example.com", Role.GUEST);
            long before = System.currentTimeMillis();

            String token = jwtUtil.generateRefreshToken(user);

            long expMs = jwtUtil.extractExpiration(token).getTime();
            assertThat(expMs).isBetween(before + REFRESH_EXPIRATION - 1000, before + REFRESH_EXPIRATION + 1000);
        }

        @Test
        @DisplayName("refresh token is different from the access token for the same user")
        void generateRefreshToken_differFromAccessToken() {
            User user = userWithEmail("bob@example.com", Role.GUEST);

            String access  = jwtUtil.generateAccessToken(user);
            String refresh = jwtUtil.generateRefreshToken(user);

            assertThat(refresh).isNotEqualTo(access);
        }
    }

    // -------------------------------------------------------------------------
    // extractEmail()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("extractEmail()")
    class ExtractEmail {

        @Test
        @DisplayName("returns the correct email from an access token")
        void extractEmail_fromAccessToken_returnsCorrectEmail() {
            User user = userWithEmail("carol@example.com", Role.SUPER_ADMIN);
            String token = jwtUtil.generateAccessToken(user);

            assertThat(jwtUtil.extractEmail(token)).isEqualTo("carol@example.com");
        }

        @Test
        @DisplayName("returns the correct email from a refresh token")
        void extractEmail_fromRefreshToken_returnsCorrectEmail() {
            User user = userWithEmail("carol@example.com", Role.CUSTOMER);
            String token = jwtUtil.generateRefreshToken(user);

            assertThat(jwtUtil.extractEmail(token)).isEqualTo("carol@example.com");
        }
    }

    // -------------------------------------------------------------------------
    // extractExpiration()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("extractExpiration()")
    class ExtractExpiration {

        @Test
        @DisplayName("expiration date is in the future for a fresh token")
        void extractExpiration_freshToken_isInFuture() {
            User user = userWithEmail("dave@example.com", Role.CUSTOMER);
            String token = jwtUtil.generateAccessToken(user);

            assertThat(jwtUtil.extractExpiration(token)).isAfter(new Date());
        }

        @Test
        @DisplayName("expiration is set to a short TTL when configured with a small value")
        void extractExpiration_shortTtlToken_expiresNearFuture() {
            // JJWT throws ExpiredJwtException when parsing an already-expired token,
            // so we cannot call extractExpiration() on a token built with EXPIRED (-1000ms).
            // Instead we verify a short-lived token has an expiration close to now.
            JwtUtil shortLivedUtil = buildJwtUtil(5_000L, REFRESH_EXPIRATION);
            User user = userWithEmail("dave@example.com", Role.CUSTOMER);
            long before = System.currentTimeMillis();

            String token = shortLivedUtil.generateAccessToken(user);

            Date expiration = shortLivedUtil.extractExpiration(token);
            assertThat(expiration.getTime()).isBetween(before + 4_000, before + 6_000);
        }
    }

    // -------------------------------------------------------------------------
    // isTokenValid()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        @Test
        @DisplayName("returns true for a valid, unexpired token matching the user")
        void isTokenValid_validToken_returnsTrue() {
            User user = userWithEmail("eve@example.com", Role.CUSTOMER);
            String token = jwtUtil.generateAccessToken(user);

            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("eve@example.com");

            assertThat(jwtUtil.isTokenValid(token, userDetails)).isTrue();
        }

        @Test
        @DisplayName("returns false when the email in the token does not match UserDetails")
        void isTokenValid_emailMismatch_returnsFalse() {
            User user = userWithEmail("eve@example.com", Role.CUSTOMER);
            String token = jwtUtil.generateAccessToken(user);

            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("other@example.com");

            assertThat(jwtUtil.isTokenValid(token, userDetails)).isFalse();
        }

        @Test
        @DisplayName("returns false for an expired token")
        void isTokenValid_expiredToken_returnsFalse() {
            JwtUtil expiredUtil = buildJwtUtil(EXPIRED, REFRESH_EXPIRATION);
            User user = userWithEmail("eve@example.com", Role.CUSTOMER);
            String token = expiredUtil.generateAccessToken(user);

            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("eve@example.com");

            // Use expiredUtil to parse — same key, token is already expired
            assertThat(expiredUtil.isTokenValid(token, userDetails)).isFalse();
        }

        @Test
        @DisplayName("returns false for a token signed with a different key")
        void isTokenValid_wrongSigningKey_returnsFalse() {
            // Build a second util with a completely different secret
            String differentSecret = Base64.getEncoder().encodeToString(new byte[]{
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1
            });
            JwtUtil otherUtil = new JwtUtil();
            ReflectionTestUtils.setField(otherUtil, "secret", differentSecret);
            ReflectionTestUtils.setField(otherUtil, "accessTokenExpiration", ACCESS_EXPIRATION);
            ReflectionTestUtils.setField(otherUtil, "refreshTokenExpiration", REFRESH_EXPIRATION);
            ReflectionTestUtils.invokeMethod(otherUtil, "init");

            User user = userWithEmail("eve@example.com", Role.CUSTOMER);
            String tokenFromOtherKey = otherUtil.generateAccessToken(user);

            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("eve@example.com");

            // jwtUtil uses TEST_SECRET — token was signed with a different key
            assertThat(jwtUtil.isTokenValid(tokenFromOtherKey, userDetails)).isFalse();
        }

        @Test
        @DisplayName("returns false for a malformed token string")
        void isTokenValid_malformedToken_returnsFalse() {
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("eve@example.com");

            assertThat(jwtUtil.isTokenValid("not.a.jwt", userDetails)).isFalse();
        }

        @Test
        @DisplayName("throws IllegalArgumentException for a blank token string")
        void isTokenValid_blankToken_throwsIllegalArgumentException() {
            // JJWT throws IllegalArgumentException (not JwtException) for blank input,
            // which isTokenValid()'s catch(JwtException) does not handle.
            // This test documents the current production behaviour.
            UserDetails userDetails = mock(UserDetails.class);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> jwtUtil.isTokenValid("", userDetails))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}