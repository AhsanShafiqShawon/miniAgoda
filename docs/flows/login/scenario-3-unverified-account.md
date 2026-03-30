# Scenario 3: Login — Unverified Account (INACTIVE)

**User:** A new user who registered but never clicked the verification link
**Action:** Attempts to log in before verifying email
**Outcome:** 403 Forbidden — account is INACTIVE

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 3 |
|---|---|---|
| User status | ACTIVE | INACTIVE |
| Status check | Passes | Fails immediately |
| BCrypt runs | Yes | No — status check comes first |
| Response time | ~312ms | ~50ms (no BCrypt) |
| HTTP Status | 200 OK | 403 Forbidden |

---

## The Conversation

*(Network layers identical. Picking up at AuthService.)*

---

**AuthService:** Received AuthRequest for unverified@example.com.

**Step 1 — Look up user:**
UserRepository, find unverified@example.com.

---

**UserRepository:**
```sql
SELECT id, email, password, role, status, ...
FROM   users
WHERE  email = 'unverified@example.com';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
id:       a1b2c3d4-e5f6-7890-abcd-ef1234567890
email:    unverified@example.com
password: $2a$12$SomeHashedPasswordXYZ...
role:     GUEST
status:   INACTIVE    ← registered but email not verified
```

---

**UserRepository:** User found. Returning to AuthService.

---

**AuthService:**

**Step 2 — Check status:**
```
user.status = INACTIVE ❌
```

Status check FAILS before password verification even begins.
No BCrypt — no unnecessary computation.

Throwing UserNotActiveException:
```java
throw new UserNotActiveException(
    "Your account is not yet verified. " +
    "Please check your email for the verification link."
);
```

---

**GlobalExceptionHandler:** Caught UserNotActiveException.

```java
@ExceptionHandler(UserNotActiveException.class)
@ResponseStatus(HttpStatus.FORBIDDEN)
public ErrorResponse handleUserNotActive(UserNotActiveException ex) {
    return new ErrorResponse(
        status:  403,
        error:   "Forbidden",
        message: ex.getMessage(),
        path:    "/api/v1/auth/login"
    );
}
```

---

**AuthController:** Building 403 response:

```json
HTTP/1.1 403 Forbidden
Content-Type: application/json
X-Request-ID: req-6f7g8h9i-0j1k-2l3m-4n5o

{
  "status":  403,
  "error":   "Forbidden",
  "message": "Your account is not yet verified. Please check your email for the verification link.",
  "path":    "/api/v1/auth/login",
  "action":  "resend-verification"
}
```

Note: HTTP 403 Forbidden — not 401 Unauthorized. The distinction:
- 401: credentials unknown or wrong
- 403: credentials valid but access denied (account state issue)

The `action: "resend-verification"` hint tells the frontend to show
a "Resend verification email" button.

---

**Browser:** Received 403. Rendering:

```
⚠️ Account Not Verified

Your account is not yet verified.
Please check your email for the verification link.

[Resend Verification Email]   [Back to Login]
```

---

**User:** I need to check my email and click the verification link.

---

## Security Notes

**Status check before password check:**
This is intentional. We check status before running BCrypt because:
- BCrypt is expensive (~200ms)
- No point running it if the account cannot log in regardless
- Response time ~50ms — notably faster than wrong password (~312ms)

This does create a minor timing difference that reveals the email
exists AND the account is unverified — but this is an acceptable
tradeoff since the message itself already reveals this clearly.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | User | Submits login before email verification |
| 2–10 | Network + Spring | Identical to Scenario 1 |
| 11 | AuthService | Look up user by email |
| 12 | UserRepository | SELECT WHERE email=? |
| 13 | PostgreSQL | User found — status=INACTIVE |
| 14 | AuthService | status=INACTIVE ❌ — throw immediately |
| 15 | AuthService | BCrypt SKIPPED — no point |
| 16 | GlobalExceptionHandler | Catch → 403 Forbidden |
| 17 | AuthController | Message + action hint for frontend |
| 18 | Return path | 403 in ~50ms (no BCrypt) |
| 19 | Browser | Shows "resend verification" option |