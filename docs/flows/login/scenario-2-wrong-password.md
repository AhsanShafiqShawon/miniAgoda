# Scenario 2: Login — Wrong Password

**User:** Shawon (registered and verified)
**Action:** Submits login with incorrect password
**Outcome:** 401 Unauthorized — no token issued

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 2 |
|---|---|---|
| Password | Correct | Wrong |
| BCrypt result | true | false |
| Token generated | Yes | No |
| HTTP Status | 200 OK | 401 Unauthorized |
| Response time | ~312ms | ~312ms (same — BCrypt always runs) |

---

## The Conversation

*(Network layers — Shawon → Browser → TLS → TCP/IP → Internet → CDN →
API Gateway → Load Balancer → Nginx → Tomcat → DispatcherServlet →
Spring Security Filter — identical to Scenario 1. Picking up at
AuthController.)*

---

**AuthController:** Deserializing:
```java
AuthRequest request = new AuthRequest(
    email:    "shawon@example.com",
    password: "WrongPassword999!"    // incorrect
);
```

@Valid passes — format is valid. Calling AuthService.authenticateUser(request).

---

**AuthService:**

**Step 1 — Look up user:**
UserRepository, find shawon@example.com.

---

**UserRepository:**
```sql
SELECT id, email, password, role, status, ...
FROM   users
WHERE  email = 'shawon@example.com';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
id:       f47ac10b-58cc-4372-a567-0e02b2c3d479
status:   ACTIVE
password: $2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh
```

Row found.

---

**UserRepository:** User found. Returning to AuthService.

---

**AuthService:**

**Step 2 — Check status:**
```
user.status = ACTIVE ✅
```

**Step 3 — Verify password:**
```
submitted:  "WrongPassword999!"
stored:     "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh"

BCrypt.checkpw("WrongPassword999!", "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh")
→ false ❌

Note: BCrypt still runs the full ~200ms computation even for wrong
passwords. This is intentional — prevents timing attacks where an
attacker could detect a wrong password by faster response time.
```

Password does not match. Throwing InvalidCredentialsException:

```java
throw new InvalidCredentialsException("Invalid email or password.");
```

Note: The message says "Invalid email or password" — not "Invalid
password." This prevents user enumeration — attacker cannot know
whether the email exists or the password was wrong.

---

**GlobalExceptionHandler:** Caught InvalidCredentialsException.

```java
@ExceptionHandler(InvalidCredentialsException.class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex) {
    return new ErrorResponse(
        status:  401,
        error:   "Unauthorized",
        message: "Invalid email or password.",
        path:    "/api/v1/auth/login"
    );
}
```

---

**AuthController:** Building 401 response:

```json
HTTP/1.1 401 Unauthorized
Content-Type: application/json
WWW-Authenticate: Bearer realm="miniAgoda"
X-Request-ID: req-5e6f7g8h-9i0j-1k2l-3m4n

{
  "status":  401,
  "error":   "Unauthorized",
  "message": "Invalid email or password.",
  "path":    "/api/v1/auth/login"
}
```

Note: `WWW-Authenticate` header tells the client what authentication
scheme is expected. No token included — obviously.

---

*(Return path — AuthController → DispatcherServlet → Tomcat → TCP/IP →
Nginx → Load Balancer → API Gateway → Cloudflare → TLS → Browser —
identical structure to Scenario 1 return path.)*

---

**Browser:** Received 401. Rendering:

```
❌ Login Failed

Invalid email or password.
Please check your credentials and try again.

[Try Again]   [Forgot Password?]
```

---

**Shawon:** I must have mistyped my password. Let me try again.

---

## Security Notes

**1. Same response time regardless of outcome (~312ms):**
BCrypt always runs — even for wrong passwords. An attacker timing
responses cannot determine whether the email exists.

**2. Generic error message:**
"Invalid email or password" — not "Email not found" or "Wrong password."
Prevents user enumeration — attacker cannot confirm valid emails.

**3. No account lockout shown here:**
In production, repeated failed attempts trigger progressive lockout:
```
1-3 failed:  no lockout
4-5 failed:  15 minute lockout
6+  failed:  account locked — admin must unlock
```
This is implemented via a `failed_login_attempts` counter on the
`users` table, incremented on each failure and reset on success.

**4. No token of any kind issued:**
Not even a partial token. 401 response is completely empty of
authentication artifacts.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Submits login with wrong password |
| 2–10 | Network + Spring | Same as Scenario 1 up to AuthService |
| 11 | AuthService | Look up user — found |
| 12 | UserRepository | SELECT WHERE email=? |
| 13 | PostgreSQL | User returned with hashed password |
| 14 | AuthService | status=ACTIVE ✅ |
| 15 | AuthService | BCrypt verify — ~200ms — returns false ❌ |
| 16 | AuthService | Throw InvalidCredentialsException |
| 17 | GlobalExceptionHandler | Catch → 401 Unauthorized |
| 18 | AuthController | Generic error — no email/password hint |
| 19 | Return path | 401 in ~312ms — same as success (timing safe) |
| 20 | Browser | Renders "Invalid email or password" |