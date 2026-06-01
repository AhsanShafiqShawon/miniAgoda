package com.miniagoda.auth.service;

import com.miniagoda.auth.dto.LoginRequest;
import com.miniagoda.auth.dto.LoginResponse;
import com.miniagoda.auth.dto.RegisterRequest;
import com.miniagoda.auth.dto.RegisterResponse;
import com.miniagoda.auth.entity.EmailVerificationToken;
import com.miniagoda.auth.entity.RefreshToken;
import com.miniagoda.auth.exception.*;
import com.miniagoda.auth.repository.EmailVerificationTokenRepository;
import com.miniagoda.auth.repository.RefreshTokenRepository;
import com.miniagoda.auth.util.JwtUtil;
import com.miniagoda.auth.util.VerificationTokenUtil;
import com.miniagoda.notification.event.AccountRegisteredNotificationEvent;
import com.miniagoda.user.entity.Role;
import com.miniagoda.user.entity.User;
import com.miniagoda.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private VerificationTokenUtil verificationTokenUtil;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HttpServletRequest httpRequest;
    @Mock private HttpServletResponse httpResponse;

    @InjectMocks
    private AuthService authService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private User buildUser() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john@example.com");
        user.setPassword("encodedPassword");
        user.setRole(Role.CUSTOMER);
        user.setVerified(false);
        return user;
    }

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setEmail("john@example.com");
        req.setPassword("password123");
        return req;
    }

    private LoginRequest buildLoginRequest() {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("password123");
        return req;
    }

    @BeforeEach
    void injectRefreshTokenExpiration() {
        // 7 days in ms
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604_800_000L);
    }

    // =========================================================================
    // register()
    // =========================================================================
    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register a new user and return an access token")
        void success() {
            RegisterRequest req = buildRegisterRequest();
            User savedUser = buildUser();

            when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
            when(passwordEncoder.encode(req.getPassword())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(verificationTokenUtil.generateToken()).thenReturn("rawToken");
            when(verificationTokenUtil.hashToken("rawToken")).thenReturn("hashedToken");
            when(verificationTokenUtil.buildVerificationLink("rawToken")).thenReturn("http://app/verify?token=rawToken");
            when(jwtUtil.generateAccessToken(savedUser)).thenReturn("accessToken");
            when(jwtUtil.generateRefreshToken(savedUser)).thenReturn("refreshToken");

            RegisterResponse response = authService.register(req, httpResponse);

            assertThat(response.getAccessToken()).isEqualTo("accessToken");

            // Verification token persisted
            ArgumentCaptor<EmailVerificationToken> evtCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
            verify(emailVerificationTokenRepository).save(evtCaptor.capture());
            assertThat(evtCaptor.getValue().getTokenHash()).isEqualTo("hashedToken");
            assertThat(evtCaptor.getValue().isUsed()).isFalse();

            // Notification event published
            verify(applicationEventPublisher).publishEvent(any(AccountRegisteredNotificationEvent.class));

            // Refresh token stored & old ones revoked
            verify(refreshTokenRepository).revokeAllByUser(savedUser);
            verify(refreshTokenRepository).save(any(RefreshToken.class));

            // HttpOnly cookie set
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(httpResponse).addCookie(cookieCaptor.capture());
            Cookie cookie = cookieCaptor.getValue();
            assertThat(cookie.getName()).isEqualTo("refreshToken");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isTrue();
        }

        @Test
        @DisplayName("should throw EmailAlreadyExistException when email is taken")
        void emailAlreadyExists() {
            RegisterRequest req = buildRegisterRequest();
            when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(buildUser()));

            assertThatThrownBy(() -> authService.register(req, httpResponse))
                    .isInstanceOf(EmailAlreadyExistException.class);

            verifyNoInteractions(passwordEncoder, jwtUtil, emailVerificationTokenRepository);
        }

        @Test
        @DisplayName("should save user with CUSTOMER role and encoded password")
        void userSavedWithCorrectFields() {
            RegisterRequest req = buildRegisterRequest();
            User savedUser = buildUser();

            when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
            when(passwordEncoder.encode(req.getPassword())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(verificationTokenUtil.generateToken()).thenReturn("t");
            when(verificationTokenUtil.hashToken(any())).thenReturn("h");
            when(verificationTokenUtil.buildVerificationLink(any())).thenReturn("link");
            when(jwtUtil.generateAccessToken(any())).thenReturn("at");
            when(jwtUtil.generateRefreshToken(any())).thenReturn("rt");

            authService.register(req, httpResponse);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User capturedUser = userCaptor.getValue();
            assertThat(capturedUser.getRole()).isEqualTo(Role.CUSTOMER);
            assertThat(capturedUser.getPassword()).isEqualTo("encoded");
        }
    }

    // =========================================================================
    // login()
    // =========================================================================
    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return access token on valid credentials")
        void success() {
            LoginRequest req = buildLoginRequest();
            User user = buildUser();

            when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(req.getPassword(), user.getPassword())).thenReturn(true);
            when(jwtUtil.generateAccessToken(user)).thenReturn("accessToken");
            when(jwtUtil.generateRefreshToken(user)).thenReturn("refreshToken");

            LoginResponse response = authService.login(req, httpResponse);

            assertThat(response.getAccessToken()).isEqualTo("accessToken");
            verify(refreshTokenRepository).revokeAllByUser(user);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
            verify(httpResponse).addCookie(any(Cookie.class));
        }

        @Test
        @DisplayName("should throw BadCredentialsException when email not found")
        void emailNotFound() {
            LoginRequest req = buildLoginRequest();
            when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req, httpResponse))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("should throw BadCredentialsException when password does not match")
        void wrongPassword() {
            LoginRequest req = buildLoginRequest();
            User user = buildUser();

            when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(req.getPassword(), user.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req, httpResponse))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Invalid email or password");

            verifyNoInteractions(jwtUtil);
        }
    }

    // =========================================================================
    // logout()
    // =========================================================================
    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("should delete refresh token, blocklist the access token, and clear the cookie")
        void success() {
            Cookie refreshCookie = new Cookie("refreshToken", "rawRefreshToken");
            when(httpRequest.getCookies()).thenReturn(new Cookie[]{refreshCookie});
            when(httpRequest.getHeader("Authorization")).thenReturn("Bearer accessToken");
            // AuthService has its own hashToken method – we test it through behaviour
            when(jwtUtil.extractJti("accessToken")).thenReturn("jti-123");
            when(jwtUtil.extractExpiration("accessToken"))
                    .thenReturn(new Date(System.currentTimeMillis() + 300_000));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            authService.logout(httpRequest, httpResponse);

            // Refresh token deleted
            verify(refreshTokenRepository).deleteByTokenHash(anyString());

            // JTI added to Redis blocklist
            verify(valueOperations).set(eq("blocklist:jti-123"), eq("1"), anyLong(), any());

            // Cookie cleared
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(httpResponse).addCookie(cookieCaptor.capture());
            assertThat(cookieCaptor.getValue().getMaxAge()).isZero();
            assertThat(cookieCaptor.getValue().getValue()).isEmpty();
        }

        @Test
        @DisplayName("should do nothing when there are no cookies")
        void noCookies() {
            when(httpRequest.getCookies()).thenReturn(null);

            authService.logout(httpRequest, httpResponse);

            verifyNoInteractions(refreshTokenRepository, redisTemplate);
            verify(httpResponse, never()).addCookie(any());
        }

        @Test
        @DisplayName("should do nothing when refreshToken cookie is absent")
        void noRefreshTokenCookie() {
            when(httpRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "value")});

            authService.logout(httpRequest, httpResponse);

            verifyNoInteractions(refreshTokenRepository, redisTemplate);
        }

        @Test
        @DisplayName("should skip blocklisting when Authorization header is missing")
        void noAuthorizationHeader() {
            Cookie refreshCookie = new Cookie("refreshToken", "rawRefreshToken");
            when(httpRequest.getCookies()).thenReturn(new Cookie[]{refreshCookie});
            when(httpRequest.getHeader("Authorization")).thenReturn(null);

            authService.logout(httpRequest, httpResponse);

            verify(refreshTokenRepository).deleteByTokenHash(anyString());
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("should skip blocklisting when access token has already expired")
        void expiredAccessToken() {
            Cookie refreshCookie = new Cookie("refreshToken", "rawRefreshToken");
            when(httpRequest.getCookies()).thenReturn(new Cookie[]{refreshCookie});
            when(httpRequest.getHeader("Authorization")).thenReturn("Bearer accessToken");
            when(jwtUtil.extractJti("accessToken")).thenReturn("jti-xyz");
            when(jwtUtil.extractExpiration("accessToken"))
                    .thenReturn(new Date(System.currentTimeMillis() - 1000)); // already expired

            authService.logout(httpRequest, httpResponse);

            verify(refreshTokenRepository).deleteByTokenHash(anyString());
            verifyNoInteractions(redisTemplate);
        }
    }

    // =========================================================================
    // refresh()
    // =========================================================================
    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("should rotate refresh token and return new access token")
        void success() {
            User user = buildUser();

            RefreshToken storedToken = new RefreshToken();
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(LocalDateTime.now().plusDays(7));
            storedToken.setUser(user);

            Cookie refreshCookie = new Cookie("refreshToken", "rawRefreshToken");
            when(httpRequest.getCookies()).thenReturn(new Cookie[]{refreshCookie});
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
            when(jwtUtil.isTokenValid(eq("rawRefreshToken"), any())).thenReturn(true);
            when(jwtUtil.generateAccessToken(user)).thenReturn("newAccessToken");
            when(jwtUtil.generateRefreshToken(user)).thenReturn("newRefreshToken");

            RegisterResponse response = authService.refresh(httpRequest, httpResponse);

            assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
            assertThat(storedToken.isRevoked()).isTrue(); // old token revoked
            verify(refreshTokenRepository).save(any(RefreshToken.class)); // new token saved
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when no cookies present")
        void noCookies() {
            when(httpRequest.getCookies()).thenReturn(null);

            assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token not found in DB")
        void tokenNotFound() {
            when(httpRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "raw")});
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token is revoked")
        void tokenRevoked() {
            User user = buildUser();
            RefreshToken storedToken = new RefreshToken();
            storedToken.setRevoked(true);
            storedToken.setExpiresAt(LocalDateTime.now().plusDays(7));
            storedToken.setUser(user);

            when(httpRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "raw")});
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

            assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when token is expired")
        void tokenExpired() {
            User user = buildUser();
            RefreshToken storedToken = new RefreshToken();
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(LocalDateTime.now().minusSeconds(1));
            storedToken.setUser(user);

            when(httpRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "raw")});
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

            assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when JWT signature is invalid")
        void jwtInvalid() {
            User user = buildUser();
            RefreshToken storedToken = new RefreshToken();
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(LocalDateTime.now().plusDays(7));
            storedToken.setUser(user);

            when(httpRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "raw")});
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
            when(jwtUtil.isTokenValid(eq("raw"), any())).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("should throw InvalidRefreshTokenException when refreshToken cookie is absent")
        void noRefreshTokenCookie() {
            when(httpRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "value")});

            assertThatThrownBy(() -> authService.refresh(httpRequest, httpResponse))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }
    }

    // =========================================================================
    // verifyEmail()
    // =========================================================================
    @Nested
    @DisplayName("verifyEmail()")
    class VerifyEmail {

        private EmailVerificationToken buildToken(boolean used, LocalDateTime expiresAt) {
            User user = buildUser();
            EmailVerificationToken evt = new EmailVerificationToken();
            evt.setTokenHash("hashedToken");
            evt.setUsed(used);
            evt.setExpiresAt(expiresAt);
            evt.setUser(user);
            return evt;
        }

        @Test
        @DisplayName("should mark token used, set user verified, and return success message")
        void success() {
            EmailVerificationToken evt = buildToken(false, LocalDateTime.now().plusHours(1));

            when(verificationTokenUtil.hashToken("rawToken")).thenReturn("hashedToken");
            when(emailVerificationTokenRepository.findByTokenHash("hashedToken"))
                    .thenReturn(Optional.of(evt));

            String result = authService.verifyEmail("rawToken");

            assertThat(result).isEqualTo("Email verified successfully");
            assertThat(evt.isUsed()).isTrue();
            assertThat(evt.getUser().isVerified()).isTrue();
            verify(emailVerificationTokenRepository).save(evt);
            verify(userRepository).save(evt.getUser());
        }

        @Test
        @DisplayName("should throw VerificationTokenNotFoundException when token not found")
        void tokenNotFound() {
            when(verificationTokenUtil.hashToken("rawToken")).thenReturn("hashedToken");
            when(emailVerificationTokenRepository.findByTokenHash("hashedToken"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyEmail("rawToken"))
                    .isInstanceOf(VerificationTokenNotFoundException.class);
        }

        @Test
        @DisplayName("should throw TokenHasExpiredException when token has expired")
        void tokenExpired() {
            EmailVerificationToken evt = buildToken(false, LocalDateTime.now().minusSeconds(1));

            when(verificationTokenUtil.hashToken("rawToken")).thenReturn("hashedToken");
            when(emailVerificationTokenRepository.findByTokenHash("hashedToken"))
                    .thenReturn(Optional.of(evt));

            assertThatThrownBy(() -> authService.verifyEmail("rawToken"))
                    .isInstanceOf(TokenHasExpiredException.class);
        }

        @Test
        @DisplayName("should throw TokenAlreadyUsedException when token has already been used")
        void tokenAlreadyUsed() {
            EmailVerificationToken evt = buildToken(true, LocalDateTime.now().plusHours(1));

            when(verificationTokenUtil.hashToken("rawToken")).thenReturn("hashedToken");
            when(emailVerificationTokenRepository.findByTokenHash("hashedToken"))
                    .thenReturn(Optional.of(evt));

            assertThatThrownBy(() -> authService.verifyEmail("rawToken"))
                    .isInstanceOf(TokenAlreadyUsedException.class);
        }
    }
}