# Scenario 3: Email Verification

**User:** Shawon (registered in Scenario 1, status=INACTIVE)
**Action:** Clicks verification link in email
**Outcome:** Account activated — status changes from INACTIVE to ACTIVE

---

## Key Differences from Scenarios 1 & 2

| Aspect | This Scenario |
|---|---|
| Starting point | Shawon already registered, email received |
| HTTP method | GET (clicking a link) |
| No request body | Token is a query parameter |
| No login required | Token itself is the credential |
| Outcome | User.status → ACTIVE, token invalidated |

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | Clicking link in email client |
| `Email Client` | Client | Opens link in Browser |
| `Browser` | Client | Builds GET request with token |
| `TLS (Client)` | Client OS | Encrypts request |
| `TCP/IP (Client)` | Client OS | Routes packets |
| `Internet` | Network | Routes to server |
| `CDN (Cloudflare)` | Edge | TLS termination, no caching |
| `API Gateway` | Infrastructure | Routing, rate limiting |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `Reverse Proxy (Nginx)` | Infrastructure | HTTP parsing |
| `TCP/IP (Server)` | Server OS | Reassembles packets |
| `HTTP Server (Tomcat)` | Server | Parses HTTP |
| `DispatcherServlet` | Spring | Routes to handler |
| `Spring Security Filter` | Spring | Public endpoint, no JWT |
| `AuthController` | Application | Maps token param to service |
| `AuthService` | Domain | Validates token, activates user |
| `TokenRepository` | Data | Looks up and invalidates token |
| `UserRepository` | Data | Updates user status to ACTIVE |
| `PostgreSQL` | Database | Executes all queries |

---

## The Conversation

---

**Shawon:** *(opening email on phone)*

I received the verification email from miniAgoda.

```
From:    noreply@miniagoda.com
To:      shawon@example.com
Subject: Verify your miniAgoda account

Hi Ahsan Shafiq,

Please verify your email address by clicking:

https://miniagoda.com/verify-email?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d

This link expires in 24 hours.
```

*(clicks the link)*

---

**Email Client:** Link clicked. Opening in default browser:
```
URL: https://miniagoda.com/verify-email?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
```

Handing to Browser.

---

**Browser:** Navigating to the verification URL.

This is a frontend route — miniAgoda.com is the frontend app (React/Next.js).
When Browser loads this page, the frontend JavaScript reads the token
from the URL and calls the backend API:

```
GET /api/v1/auth/verify-email?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
HTTP/1.1
Host: api.miniagoda.com
Accept: application/json
User-Agent: Mozilla/5.0 (Mobile)
```

No Authorization header — the token itself is the credential.
Handing to TLS.

---

**TLS Layer (Client):** New TLS 1.3 session — Shawon is on mobile,
different device from registration. Performing handshake:

```
ClientHello → server certificate verified ✅
Keys derived → session established
```

Encrypting GET request with AES-256-GCM. The token in the URL is now
encrypted. Handing to TCP/IP.

---

**TCP/IP (Client OS):**
```
Packet 1:
  Source IP:        10.0.0.15    (Shawon's mobile)
  Destination IP:   104.21.45.23 (Cloudflare Bangkok)
  Source Port:      43210
  Destination Port: 443
```

Routing to Cloudflare.

---

**Internet:** Routing from Shawon's mobile network. ~5ms to Cloudflare.

---

**CDN (Cloudflare):** Received packets.

**TLS Termination:**
```
GET /api/v1/auth/verify-email?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
```

**Cache check:**
Verification endpoints are never cached — tokens are single-use.
Cache miss by design.

**Forward to API Gateway:**
```
GET /api/v1/auth/verify-email?token=9b1deb4d-...
X-Forwarded-For: 10.0.0.15
CF-Ray:          2c3d4e5f6g7h8i9j-BKK
```

---

**API Gateway:**

**Rate limiting:**
```
Key:    rate_limit:ip:10.0.0.15:verify-email
Count:  1 attempt
Limit:  10 per hour (prevent token brute-forcing)
Status: PASS ✅
```

**JWT check:** No Authorization header. Public endpoint.

**Routing:**
```
GET /api/v1/auth/verify-email
→ miniagoda-app:8080
```

Forwarding with X-Request-ID: req-3c4d5e6f-7g8h-9i0j-1k2l

---

**Load Balancer:**
```
Instance 1: miniagoda-app-1:8080 — connections: 28
Instance 2: miniagoda-app-2:8080 — connections: 33
Instance 3: miniagoda-app-3:8080 — connections: 25

Selected: miniagoda-app-3:8080
```

---

**Reverse Proxy (Nginx):** Forwarding to Tomcat on app-3.

---

**TCP/IP (Server OS):** Reassembling. Placing in Tomcat buffer.

---

**HTTP Server (Tomcat):** Thread picked up request.
```
Method:  GET
URI:     /api/v1/auth/verify-email
Query:   token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:**
```
Authorization header: NOT PRESENT
```

`/api/v1/auth/verify-email` is `@PreAuthorize("permitAll()")`.
No authentication required — the token IS the authentication.
AnonymousAuthenticationToken. Passing through.

---

**DispatcherServlet:** Routing:
```
GET /api/v1/auth/verify-email
→ AuthController.verifyEmail()
  @GetMapping("/api/v1/auth/verify-email")
  @PreAuthorize("permitAll()")
```

---

**AuthController:** Received request.

Extracting token from query parameter:
```java
String token = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d";
```

Calling AuthService.verifyEmail(token).

---

**AuthService:** Received token "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d".

**Step 1 — Look up token:**
TokenRepository, find this EMAIL_VERIFICATION token.

---

**TokenRepository:** Generating SQL:
```sql
SELECT id, user_id, token, type, expires_at, used, created_at
FROM   tokens
WHERE  token = '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d'
AND    type  = 'EMAIL_VERIFICATION';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(tokens table)*
```
id          | user_id                              | token                                | type               | expires_at              | used  | created_at
------------|--------------------------------------|--------------------------------------|--------------------|-------------------------|-------|------------------------
token-uuid  | f47ac10b-58cc-4372-a567-0e02b2c3d479 | 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d | EMAIL_VERIFICATION | 2024-12-19T10:15:23Z    | false | 2024-12-18T10:15:23Z

Row found.
```

---

**TokenRepository:** Token found. Returning to AuthService.

---

**AuthService:**

**Step 2 — Check if already used:**
```
token.used = false ✅ — not yet used
```

**Step 3 — Check expiry:**
```
token.expiresAt = 2024-12-19T10:15:23Z
now             = 2024-12-18T10:45:00Z
expired?        = NO ✅ — token is within 24 hours
```

**Step 4 — Invalidate token immediately:**
TokenRepository, mark this token as used — prevent replay attacks.

---

**TokenRepository:** Generating SQL:
```sql
UPDATE tokens
SET    used       = true,
       updated_at = '2024-12-18T10:45:01Z'
WHERE  id = 'token-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(tokens table)*
```
UPDATE 1 ✅

Token is now marked used = true.
Any future attempt to use this same token will be rejected.
```

---

**TokenRepository:** Token invalidated. Returning to AuthService.

---

**AuthService:** Token invalidated.

**Step 5 — Activate user:**
UserRepository, set user f47ac10b... status to ACTIVE.

---

**UserRepository:** Generating SQL:
```sql
UPDATE users
SET    status     = 'ACTIVE',
       updated_at = '2024-12-18T10:45:01Z'
WHERE  id = 'f47ac10b-58cc-4372-a567-0e02b2c3d479'
AND    status = 'INACTIVE';
```

Note: `AND status = 'INACTIVE'` — defensive check. If somehow status
was already ACTIVE (race condition), this UPDATE affects 0 rows safely.

PostgreSQL, execute.

---

**PostgreSQL:** *(users table)*
```
Before:
  id: f47ac10b-...  status: INACTIVE

After:
  id: f47ac10b-...  status: ACTIVE

UPDATE 1 ✅
```

Shawon's account is now ACTIVE. He can log in.

---

**UserRepository:** Update confirmed. Returning to AuthService.

---

**AuthService:** User activated successfully. Returning to AuthController.

---

**AuthController:** Verification complete. Building success response:

```json
HTTP/1.1 200 OK
Content-Type: application/json
X-Request-ID: req-3c4d5e6f-7g8h-9i0j-1k2l

{
  "message": "Email verified successfully. Your account is now active.",
  "status":  "ACTIVE"
}
```

---

**DispatcherServlet:** Writing 200 response. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing to bytes. Placing in socket buffer.

---

**TCP/IP (Server OS):** Breaking into packets. Sending through Nginx.

---

**Reverse Proxy (Nginx):** Adding:
```
X-Served-By:     miniagoda-app-3
X-Response-Time: 89ms
```
Forwarding to Load Balancer.

---

**Load Balancer:** Forwarding to API Gateway.

---

**API Gateway:** Logging:
```
Method:   GET /api/v1/auth/verify-email
User:     anonymous (token auth)
Status:   200 OK
Duration: 94ms
```
Forwarding to Cloudflare.

---

**CDN (Cloudflare):** 200 response. Not caching — single-use endpoint.
Forwarding to Shawon's mobile.

---

**TLS Layer (Client):** Decrypting response. Handing to Browser.

---

**Browser:** Received 200. The frontend JavaScript reads the response
and renders:

```
✅ Email Verified!

Your account is now active, Ahsan Shafiq.

Welcome to miniAgoda!

[Log In to Your Account]
```

---

**Shawon:** My account is verified. Let me log in now.

---

## Edge Cases Handled

**What if the token has expired?**
```
AuthService detects: token.expiresAt < now
Throws: TokenExpiredException
Response: 400 Bad Request
  {
    "message": "Verification link has expired. Please request a new one.",
    "action":  "resend-verification"
  }
```

**What if the token has already been used?**
```
AuthService detects: token.used = true
Throws: InvalidTokenException
Response: 400 Bad Request
  {
    "message": "This verification link has already been used.",
    "action":  "login"
  }
```

**What if the token doesn't exist?**
```
TokenRepository returns null
AuthService throws: InvalidTokenException
Response: 400 Bad Request
  {
    "message": "Invalid verification link.",
    "action":  "resend-verification"
  }
```

Note: All three error cases return the same HTTP 400. Callers cannot
distinguish between expired, used, or invalid tokens — this prevents
attackers from learning which tokens exist.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Clicks verification link in email |
| 2 | Email Client | Opens link in Browser |
| 3 | Browser | GET with token as query param — no JWT |
| 4 | TLS (Client) | New TLS 1.3 handshake (mobile device) |
| 5 | TCP/IP (Client) | Packets to Cloudflare |
| 6 | Internet | Route to Cloudflare Bangkok |
| 7 | CDN | TLS terminate, no cache |
| 8 | API Gateway | Rate limit ✅, public endpoint |
| 9 | Load Balancer | Select app-3 |
| 10 | Nginx | Forward to Tomcat |
| 11 | Tomcat | Parse GET request |
| 12 | Spring Security | No JWT → public endpoint passes |
| 13 | AuthController | Extract token from query param |
| 14 | AuthService | Look up token |
| 15 | TokenRepository | SELECT token WHERE token=? AND type=VERIFICATION |
| 16 | PostgreSQL | Token found — used=false, not expired |
| 17 | AuthService | used=false ✅, not expired ✅ |
| 18 | TokenRepository | UPDATE tokens SET used=true |
| 19 | PostgreSQL | Token invalidated — replay attacks prevented |
| 20 | AuthService | Activate user |
| 21 | UserRepository | UPDATE users SET status=ACTIVE |
| 22 | PostgreSQL | User status: INACTIVE → ACTIVE |
| 23 | AuthController | 200 OK — account activated |
| 24 | Return path | Response back through all layers |
| 25 | Browser | Renders "Email Verified!" with login button |