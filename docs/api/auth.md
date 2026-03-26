# API Contract: Auth

## Overview

`AuthService` is responsible for authentication, token management,
email verification, and password management. It is NOT responsible
for creating or managing user accounts (that's `UserService`), admin
user management (that's `AdminService`), or sending emails (that's
`NotificationService`).

Role-based authorization is handled by Spring Security annotations
(`@PreAuthorize`) — not by an explicit service method. See
[ADR-008](../architecture/decisions/ADR-008-spring-security-authorization.md).

## Collaborators

```java
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
}
```

---

## Methods

### `authenticateUser(AuthRequest request)`

Authenticates a user and returns a JWT token pair.

```java
AuthResponse authenticateUser(AuthRequest request);
```

**Behavior:**
1. Look up user by email — throw `ResourceNotFoundException` if not found
2. Verify password hash — throw `InvalidCredentialsException` if wrong
3. Check user status — throw `UserNotActiveException` if `INACTIVE` or `BANNED`
4. Generate access token and refresh token
5. Store refresh token in `TokenRepository`
6. Return `AuthResponse`

---

### `verifyEmail(String token)`

Verifies a user's email after registration. Sets user status to `ACTIVE`.

```java
void verifyEmail(String token);
```

**Behavior:**
1. Look up token in `TokenRepository` — throw `InvalidTokenException` if not found
2. Check token expiry — throw `TokenExpiredException` if expired
3. Set user status to `ACTIVE`
4. Invalidate the verification token

---

### `refreshToken(String token)`

Issues a new access token using a valid refresh token.

```java
String refreshToken(String token);
```

**Behavior:**
1. Look up refresh token — throw `InvalidTokenException` if not found
2. Check token expiry — throw `TokenExpiredException` if expired
3. Generate and return new access token

---

### `validateToken(String token)`

Validates an access token. Used by Spring Security JWT filter.

```java
void validateToken(String token);
```

---

### `revokeToken(String token)`

Revokes a specific token. Used for targeted token invalidation.

```java
void revokeToken(String token);
```

---

### `logoutUser(UUID userId)`

Revokes all active tokens for a user. Full logout across all devices.

```java
void logoutUser(UUID userId);
```

---

### `changePassword(UUID userId, ChangePasswordRequest request)`

Changes a user's password after verifying the current password.

```java
void changePassword(UUID userId, ChangePasswordRequest request);
```

**Behavior:**
1. Look up user — throw `ResourceNotFoundException` if not found
2. Verify `currentPassword` — throw `InvalidCredentialsException` if wrong
3. Hash and store `newPassword`
4. Revoke all existing tokens — user must log in again
5. Set `updatedAt`

---

### `requestPasswordReset(String email)`

Generates a password reset token and sends reset email asynchronously.

```java
void requestPasswordReset(String email);
```

**Behavior:**
1. Look up user by email — silently ignore if not found (security best practice)
2. Generate password reset token
3. Store token in `TokenRepository` with expiry
4. Call `NotificationService.sendPasswordResetEmail()` async

---

### `confirmPasswordReset(String token, String newPassword)`

Resets password using a valid reset token.

```java
void confirmPasswordReset(String token, String newPassword);
```

**Behavior:**
1. Look up token — throw `InvalidTokenException` if not found
2. Check token expiry — throw `TokenExpiredException` if expired
3. Hash and store `newPassword`
4. Invalidate the reset token
5. Revoke all existing tokens — user must log in again

---

## Error Cases

| Condition | Exception |
|---|---|
| User not found | `ResourceNotFoundException` |
| Wrong password | `InvalidCredentialsException` |
| User is `INACTIVE` or `BANNED` | `UserNotActiveException` |
| Invalid or missing token | `InvalidTokenException` |
| Expired token | `TokenExpiredException` |