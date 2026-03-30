# Scenario 4: Login — Banned Account

**User:** A user whose account has been banned by an admin
**Action:** Attempts to log in
**Outcome:** 403 Forbidden — account is BANNED

---

## Key Differences from Scenario 3

| Aspect | Scenario 3 | Scenario 4 |
|---|---|---|
| User status | INACTIVE (unverified) | BANNED (admin action) |
| Reason | Never verified email | Violated terms of service |
| Action hint | resend-verification | contact-support |
| HTTP Status | 403 Forbidden | 403 Forbidden |

---

## The Conversation

*(Network layers identical. Picking up at AuthService.)*

---

**AuthService:** Received AuthRequest for banned@example.com.

**Step 1 — Look up user:**
UserRepository, find banned@example.com.

---

**UserRepository:**
```sql
SELECT id, email, password, role, status, ...
FROM   users
WHERE  email = 'banned@example.com';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
id:       b2c3d4e5-f6g7-8901-bcde-f12345678901
email:    banned@example.com
password: $2a$12$AnotherHashedPasswordABC...
role:     GUEST
status:   BANNED    ← banned by admin via AdminService.banUser()
```

---

**UserRepository:** User found. Returning to AuthService.

---

**AuthService:**

**Step 2 — Check status:**
```
user.status = BANNED ❌
```

Throwing UserNotActiveException — same exception class as INACTIVE,
but different message:

```java
throw new UserNotActiveException(
    "Your account has been suspended. " +
    "Please contact support for assistance."
);
```

Note: We intentionally do NOT tell the user why they were banned or
who banned them. "Contact support" is the only path forward.

---

**GlobalExceptionHandler:** Same handler as Scenario 3.
403 Forbidden.

---

**AuthController:** Building 403 response:

```json
HTTP/1.1 403 Forbidden
Content-Type: application/json
X-Request-ID: req-7g8h9i0j-1k2l-3m4n-5o6p

{
  "status":  403,
  "error":   "Forbidden",
  "message": "Your account has been suspended. Please contact support for assistance.",
  "path":    "/api/v1/auth/login",
  "action":  "contact-support"
}
```

---

**Browser:** Received 403. Rendering:

```
🚫 Account Suspended

Your account has been suspended.
Please contact support for assistance.

[Contact Support]   [Back to Login]
```

---

**Banned User:** I need to contact miniAgoda support to understand
what happened to my account.

---

## How a Ban is Applied

For context — this is how `AdminService.banUser()` sets the BANNED
status (from the Admin flow):

```sql
UPDATE users
SET    status     = 'BANNED',
       updated_at = NOW()
WHERE  id = 'b2c3d4e5-f6g7-8901-bcde-f12345678901';
```

And existing tokens are revoked:
```sql
UPDATE tokens
SET    used       = true,
       updated_at = NOW()
WHERE  user_id = 'b2c3d4e5-f6g7-8901-bcde-f12345678901'
AND    used    = false;
```

This means even if the banned user has an active JWT, their refresh
token is invalidated. The access token itself remains cryptographically
valid until its 24-hour expiry — this is a known JWT tradeoff. For
immediate revocation, a token blacklist (Redis) would be needed.

---

## Security Notes

**BANNED vs INACTIVE — same exception, different message:**
Both use `UserNotActiveException` and HTTP 403. The distinction is
in the user-facing message and the `action` hint — not the HTTP status.
This keeps the exception handling simple while providing useful feedback.

**BCrypt skipped for BANNED too:**
Status check runs before password verification for both INACTIVE and
BANNED accounts. No unnecessary computation.

**Token revocation on ban:**
When admin bans a user, all their refresh tokens are invalidated
immediately. However, existing access tokens (24h JWT) remain valid
until natural expiry — a known limitation of stateless JWT. If
immediate revocation is required, a Redis token blacklist is needed
(future enhancement).

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Banned user | Attempts to log in |
| 2–10 | Network + Spring | Identical to Scenario 1 |
| 11 | AuthService | Look up user by email |
| 12 | UserRepository | SELECT WHERE email=? |
| 13 | PostgreSQL | User found — status=BANNED |
| 14 | AuthService | status=BANNED ❌ — throw immediately |
| 15 | AuthService | BCrypt SKIPPED |
| 16 | GlobalExceptionHandler | 403 Forbidden |
| 17 | AuthController | "contact-support" action hint |
| 18 | Return path | 403 in ~50ms |
| 19 | Browser | Shows "Account Suspended" with support link |