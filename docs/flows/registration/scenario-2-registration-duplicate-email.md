# Scenario 2: Registration — Duplicate Email

**User:** Shawon (attempting to register with an already-used email)
**Action:** Fills registration form with existing email
**Outcome:** Registration rejected at email uniqueness check — no user created

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 2 |
|---|---|---|
| Email | New — not in database | Already registered |
| User created | Yes | No |
| Token generated | Yes | No |
| Email sent | Yes | No |
| HTTP Status | 201 Created | 409 Conflict |

---

## The Cast

Same as Scenario 1, but the conversation ends much earlier —
at `UserRepository` when the duplicate is detected.

---

## The Conversation

---

**Shawon:** I want to register with shawon@example.com.
*(This email was already registered in Scenario 1)*

---

**Browser:** Building HTTP POST:

```
POST /api/v1/auth/register
HTTP/1.1
Host: api.miniagoda.com
Content-Type: application/json

{
  "firstName":         "Ahsan Shafiq",
  "lastName":          "Shawon",
  "email":             "shawon@example.com",
  "password":          "AnotherPassword456!",
  "preferredCurrency": "BDT",
  "phoneNumber": {
    "countryCode": "+880",
    "number":      "1712345678"
  }
}
```

Handing to TLS.

---

**TLS Layer (Client):** Existing TLS 1.3 session with api.miniagoda.com —
no new handshake needed. Encrypting with AES-256-GCM. Handing to TCP/IP.

---

**TCP/IP (Client OS):**
```
Packet 1:
  Source IP:        192.168.1.105
  Destination IP:   104.21.45.23
  Source Port:      55456
  Destination Port: 443
```

Routing to Cloudflare.

---

**Internet:** Routing packets. ~3ms to Cloudflare Bangkok.

---

**CDN (Cloudflare):** TLS termination. POST — never cached.

Bot check:
```
Rate from 192.168.1.105: 2 registration attempts this hour
Limit: 5 per hour
Status: PASS ✅
```

Forwarding to API Gateway.

---

**API Gateway:** Rate limit check:
```
Key:    rate_limit:ip:192.168.1.105:register
Count:  2 attempts this hour
Limit:  5 per hour
Status: PASS ✅
```

Public endpoint — no JWT required. Routing to miniagoda-app:8080.

---

**Load Balancer:** Selecting instance with least connections.
Forwarding to miniagoda-app-2:8080.

---

**Reverse Proxy (Nginx):** Forwarding to Tomcat on app-2.

---

**TCP/IP (Server OS):** Reassembling. Placing in Tomcat buffer.

---

**HTTP Server (Tomcat):** Parsing POST body. Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:** No JWT. Public endpoint. AnonymousAuthenticationToken.
Passing to DispatcherServlet.

---

**DispatcherServlet:** Routing to UserController.register().

---

**UserController:** Deserializing JSON → RegisterRequest.

```java
RegisterRequest request = new RegisterRequest(
    firstName:         "Ahsan Shafiq",
    lastName:          "Shawon",
    email:             "shawon@example.com",
    password:          "AnotherPassword456!",
    phoneNumber:       PhoneNumber("+880", "1712345678"),
    preferredCurrency: "BDT"
);
```

Running @Valid:
```
firstName:         not blank ✅
lastName:          not blank ✅
email:             valid format ✅
password:          not blank ✅
preferredCurrency: 3 characters ✅
phoneNumber:       valid ✅
```

Bean validation passed. Calling UserService.registerUser(request).

---

**UserService:** Received RegisterRequest.

**Step 1 — Check email uniqueness:**
UserRepository, does shawon@example.com already exist?

---

**UserRepository:** Generating SQL:
```sql
SELECT COUNT(*)
FROM   users
WHERE  email = 'shawon@example.com';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(users table)*
```
count
-----
1

A user with this email already exists.
(Registered in Scenario 1 with id: f47ac10b-58cc-4372-a567-0e02b2c3d479)
```

---

**UserRepository:** Count = 1. Email already taken. Returning to
UserService.

---

**UserService:** Email is already registered.

I must stop here. No password hashing, no user creation, no token
generation, no email sending. Throwing DuplicateEmailException:

```java
throw new DuplicateEmailException(
    "An account with email shawon@example.com already exists."
);
```

This exception propagates up to UserController.

---

**UserController:** Caught DuplicateEmailException.

Spring's `@ControllerAdvice` (GlobalExceptionHandler) intercepts this:

```java
@ExceptionHandler(DuplicateEmailException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleDuplicateEmail(DuplicateEmailException ex) {
    return new ErrorResponse(
        status:  409,
        error:   "Conflict",
        message: "An account with this email already exists.",
        path:    "/api/v1/auth/register"
    );
}
```

Serializing error response to JSON:
```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-2b3c4d5e-6f7g-8h9i-0j1k

{
  "status":  409,
  "error":   "Conflict",
  "message": "An account with this email already exists.",
  "path":    "/api/v1/auth/register"
}
```

Note: HTTP 409 Conflict — the request conflicts with existing state.
The password Shawon entered is NOT echoed back in the error response.
No information about the existing account is revealed (security).

---

**DispatcherServlet:** Writing 409 response. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing to bytes. Placing in socket buffer.

---

**TCP/IP (Server OS):** Breaking into packets. Sending through Nginx.

---

**Reverse Proxy (Nginx):** Adding:
```
X-Served-By:     miniagoda-app-2
X-Response-Time: 47ms
```

47ms — much faster than Scenario 1 (234ms). No hashing, no token
generation, no email sending. Forwarding to Load Balancer.

---

**Load Balancer:** Forwarding to API Gateway.

---

**API Gateway:** Logging:
```
Method:   POST /api/v1/auth/register
User:     anonymous
Status:   409 Conflict
Duration: 52ms
```
Forwarding to Cloudflare.

---

**CDN (Cloudflare):** 409 response — not cached. Forwarding to Shawon.

---

**TLS Layer (Client):** Decrypting response. Handing to Browser.

---

**Browser:** Received 409 response. Rendering error:

```
⚠️ Registration Failed

An account with this email already exists.

Already have an account? [Log In]
Forgot your password?    [Reset Password]
```

---

**Shawon:** Ah — I already have an account with this email. Let me log in.

---

## Security Notes

This scenario demonstrates several security best practices:

**1. Timing attack resistance:**
The response time (47ms) is notably faster than a successful registration
(234ms) — because we skip hashing and email sending. In production, a
small artificial delay should be added to prevent timing-based user
enumeration attacks. An attacker timing responses could deduce whether
an email exists.

**2. No information leakage:**
The error message says "already exists" — which does reveal that the
email is registered. Some systems use a generic message like "If this
email is not registered, you will receive a verification email" to avoid
confirming whether an account exists. miniAgoda opts for clarity here
since it improves UX, but this is a deliberate tradeoff.

**3. Password not echoed:**
The password Shawon submitted is never included in any response —
success or failure.

**4. No partial state:**
If email check fails, nothing is created — no orphaned user records,
no orphaned tokens, no emails sent. Atomicity is maintained.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Submits form with existing email |
| 2 | Browser | HTTP POST |
| 3–9 | Network layers | TLS → packets → CDN → Gateway → LB → Nginx |
| 10 | Tomcat | Parse POST body |
| 11 | Spring Security | Anonymous → public endpoint |
| 12 | UserController | Deserialize → RegisterRequest, @Valid passes |
| 13 | UserService | Check email uniqueness |
| 14 | UserRepository | SELECT COUNT(*) WHERE email=? |
| 15 | PostgreSQL | Count = 1 — email taken |
| 16 | UserService | Throw DuplicateEmailException — stop immediately |
| 17 | GlobalExceptionHandler | Catch exception → 409 Conflict |
| 18 | UserController | Serialize error JSON — password NOT echoed |
| 19 | Return path | 409 travels back through all layers in 47ms |
| 20 | Browser | Renders "already exists" error with login link |