# Scenario 1: Successful Login

**User:** Shawon (registered and verified in registration flows)
**Action:** Submits login form with correct credentials
**Outcome:** JWT access + refresh token pair issued, immediately used for search

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Builds HTTP POST, stores tokens |
| `TLS (Client)` | Client OS | Encrypts credentials |
| `TCP/IP (Client)` | Client OS | Routes packets |
| `Internet` | Network | Routes to server |
| `CDN (Cloudflare)` | Edge | TLS termination |
| `API Gateway` | Infrastructure | Rate limiting, routing |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `Reverse Proxy (Nginx)` | Infrastructure | HTTP parsing |
| `TCP/IP (Server)` | Server OS | Reassembles packets |
| `HTTP Server (Tomcat)` | Server | Parses HTTP |
| `DispatcherServlet` | Spring | Routes to handler |
| `Spring Security Filter` | Spring | Public endpoint — no JWT yet |
| `AuthController` | Application | Maps request to service |
| `AuthService` | Domain | Verifies credentials, issues tokens |
| `UserRepository` | Data | Fetches user by email |
| `TokenRepository` | Data | Stores refresh token |
| `PostgreSQL` | Database | Executes queries |
| `JwtUtil` | Utility | Generates and signs JWT tokens |

---

## Part 1: Login

---

**Shawon:** I want to log in to miniAgoda.
*(fills login form)*

```
Email:    shawon@example.com
Password: MySecurePass123!
```

*(clicks Log In)*

---

**Browser:** Building HTTP POST:

```
POST /api/v1/auth/login
HTTP/1.1
Host: api.miniagoda.com
Content-Type: application/json
Accept: application/json
User-Agent: Mozilla/5.0

{
  "email":    "shawon@example.com",
  "password": "MySecurePass123!"
}
```

No Authorization header — Shawon has no token yet. Handing to TLS.

---

**TLS Layer (Client):** Existing TLS 1.3 session with api.miniagoda.com.
No new handshake needed. Encrypting with AES-256-GCM:

```
[TLS Record]
Content Type: Application Data (23)
Length:       98 bytes

[Encrypted Payload]
4f7a3b2c1d8e... (password now completely opaque)

[Authentication Tag]
a9b8c7d6e5f4...
```

Handing to TCP/IP.

---

**TCP/IP (Client OS):**
```
Packet 1:
  Source IP:        192.168.1.105
  Destination IP:   104.21.45.23   (Cloudflare Bangkok)
  Source Port:      56789
  Destination Port: 443
  Sequence No:      1001
  Flags:            PSH, ACK
```

Routing to Cloudflare Bangkok PoP.

---

**Internet:** ~3ms to Cloudflare Bangkok.

---

**CDN (Cloudflare):** Received packets.

**TLS Termination:**
```
POST /api/v1/auth/login
{
  "email":    "shawon@example.com",
  "password": "MySecurePass123!"
}
```

**Cache check:** POST — never cached.

**Bot protection:**
```
Rate from 192.168.1.105: 1 login attempt in last 60 seconds
Threshold: 10 attempts per minute per IP (login brute-force protection)
Status: PASS ✅
```

Forwarding to API Gateway.

---

**API Gateway:**

**Rate limiting:**
```
Key:    rate_limit:ip:192.168.1.105:login
Count:  1 attempt in last 60 seconds
Limit:  10 per minute (stricter than search — credential endpoint)
Status: PASS ✅
```

**JWT check:** No Authorization header. `/api/v1/auth/login` is public.

**Routing:** → miniagoda-app:8080

Forwarding with X-Request-ID: req-4d5e6f7g-8h9i-0j1k-2l3m

---

**Load Balancer:**
```
Instance 1: miniagoda-app-1:8080 — connections: 25
Instance 2: miniagoda-app-2:8080 — connections: 31
Instance 3: miniagoda-app-3:8080 — connections: 22

Selected: miniagoda-app-3:8080
```

---

**Reverse Proxy (Nginx):** Forwarding to Tomcat on app-3.

---

**TCP/IP (Server OS):** Reassembling. Placing in Tomcat buffer.

---

**HTTP Server (Tomcat):** Thread picked up request.
```
Method:       POST
URI:          /api/v1/auth/login
Content-Type: application/json
Body:         { "email": "shawon@example.com", "password": "..." }
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:** No JWT present. Public endpoint.
AnonymousAuthenticationToken. Passing through.

---

**DispatcherServlet:** Routing:
```
POST /api/v1/auth/login
→ AuthController.login()
  @PostMapping("/api/v1/auth/login")
  @PreAuthorize("permitAll()")
```

---

**AuthController:** Received request.

Deserializing:
```java
AuthRequest request = new AuthRequest(
    email:    "shawon@example.com",
    password: "MySecurePass123!"
);
```

Running @Valid:
```
email:    valid format ✅
password: not blank ✅
```

Calling AuthService.authenticateUser(request).

---

**AuthService:** Received AuthRequest.

**Step 1 — Look up user by email:**
UserRepository, find user with email shawon@example.com.

---

**UserRepository:** Generating SQL:
```sql
SELECT id, first_name, last_name, email, password,
       phone_country_code, phone_number,
       primary_image_id, preferred_currency,
       role, status, created_at, updated_at
FROM   users
WHERE  email = 'shawon@example.com';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(users table)*
```
id:                f47ac10b-58cc-4372-a567-0e02b2c3d479
first_name:        Ahsan Shafiq
last_name:         Shawon
email:             shawon@example.com
password:          $2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh
phone_country_code: +880
phone_number:      1712345678
primary_image_id:  NULL
preferred_currency: BDT
role:              GUEST
status:            ACTIVE
created_at:        2024-12-18T10:15:23Z
updated_at:        2024-12-18T10:45:01Z
```

Row found.

---

**UserRepository:** User found. Returning to AuthService.

---

**AuthService:**

**Step 2 — Check user status:**
```
user.status = ACTIVE ✅
```

Not INACTIVE (unverified) — would throw UserNotActiveException.
Not BANNED — would throw UserNotActiveException.

**Step 3 — Verify password:**

BCrypt verification — comparing submitted password against stored hash:
```
submitted:  "MySecurePass123!"
stored:     "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh"

BCrypt.checkpw("MySecurePass123!", "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh")
→ true ✅

Note: BCrypt re-hashes the submitted password with the salt extracted
from the stored hash and compares. The original password is never
recoverable. This takes ~100-300ms deliberately — brute-force protection.
```

Password matches.

**Step 4 — Generate JWT access token:**
JwtUtil, generate access token for Shawon.

---

**JwtUtil:** Building JWT access token:

```
Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload:
{
  "sub":  "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "role": "GUEST",
  "email": "shawon@example.com",
  "iat": 1703692523,    (2024-12-18T10:55:23Z — issued at)
  "exp": 1703778923     (2024-12-19T10:55:23Z — expires in 24 hours)
}

Signature:
HMAC-SHA256(
  base64url(header) + "." + base64url(payload),
  SECRET_KEY
) → "abc123xyz456..."
```

Assembled access token:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
eyJzdWIiOiJmNDdhYzEwYi01OGNjLTQzNzItYTU2Ny0wZTAyYjJjM2Q0NzkiLCJyb2xlIjoiR1VFU1QiLCJlbWFpbCI6InNoYXdvbkBleGFtcGxlLmNvbSIsImlhdCI6MTcwMzY5MjUyMywiZXhwIjoxNzAzNzc4OTIzfQ.
abc123xyz456def789ghi012jkl345mno678
```

Returning access token to AuthService.

---

**AuthService:**

**Step 5 — Generate refresh token:**
JwtUtil, generate refresh token for Shawon.

---

**JwtUtil:** Building JWT refresh token:

```
Payload:
{
  "sub":  "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "type": "REFRESH",
  "iat":  1703692523,
  "exp":  1706284523    (2024-01-26T10:55:23Z — expires in 30 days)
}
```

Refresh token:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
eyJzdWIiOiJmNDdhYzEwYi01OGNjLTQzNzItYTU2Ny0wZTAyYjJjM2Q0NzkiLCJ0eXBlIjoiUkVGUkVTSCIsImlhdCI6MTcwMzY5MjUyMywiZXhwIjoxNzA2Mjg0NTIzfQ.
refresh789abc012def345ghi678jkl901mno
```

Returning refresh token to AuthService.

---

**AuthService:**

**Step 6 — Store refresh token:**
TokenRepository, save this refresh token.

---

**TokenRepository:** Generating SQL:
```sql
INSERT INTO tokens (
    id, user_id, token, type, expires_at, used, created_at
) VALUES (
    UUID.randomUUID(),
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIi...',
    'REFRESH_TOKEN',
    '2025-01-26T10:55:23Z',
    false,
    '2024-12-18T10:55:23Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(tokens table)*
```
INSERT 0 1 ✅

Refresh token stored. Access tokens are NOT stored —
they are stateless and self-validating via signature.
```

---

**TokenRepository:** Stored. Returning to AuthService.

---

**AuthService:** Both tokens ready. Building AuthResponse:

```java
AuthResponse response = new AuthResponse(
    accessToken:  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIi...abc123xyz456",
    refreshToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIi...refresh789abc",
    expiresAt:    LocalDateTime.of(2024, 12, 19, 10, 55, 23)
);
```

Returning to AuthController.

---

**AuthController:** Received AuthResponse. Serializing:

```json
HTTP/1.1 200 OK
Content-Type: application/json
X-Request-ID: req-4d5e6f7g-8h9i-0j1k-2l3m

{
  "accessToken":  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIi...abc123xyz456",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIi...refresh789abc",
  "expiresAt":    "2024-12-19T10:55:23Z"
}
```

---

**DispatcherServlet:** Writing 200 response. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing to bytes. Socket buffer.

---

**TCP/IP (Server OS):** Packets through Nginx.

---

**Reverse Proxy (Nginx):**
```
X-Served-By:     miniagoda-app-3
X-Response-Time: 312ms
```

312ms — slightly longer than registration because BCrypt verification
(~100-300ms) is included.

---

**Load Balancer → API Gateway → Cloudflare → TLS (Client) → Browser.**

---

**Browser:** Received 200 with token pair.

Storing tokens securely:
```javascript
// Access token — in memory (JavaScript variable)
// NOT in localStorage — XSS protection
const accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456";

// Refresh token — in httpOnly cookie (cannot be read by JS)
// Set via Set-Cookie header if server uses cookie strategy
// OR also in memory if using pure bearer token strategy
const refreshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...refresh789abc";
```

Rendering UI:
```
✅ Welcome back, Ahsan Shafiq!

Redirecting to home page...
```

---

**Shawon:** I am logged in. Let me search for hotels in Bangkok.

---

## Part 2: Using the Token — First Authenticated Request

Shawon immediately searches for hotels. This connects to Search
Scenario 1 — but now we see where the JWT came from.

---

**Browser:** Building search request. Attaching the access token
received from login:

```
GET /api/v1/hotels/search
  ?cityId=550e8400-e29b-41d4-a716-446655440000
  &checkIn=2024-12-20
  &checkOut=2024-12-25
  &guestCount=2
  &amenities=WIFI,POOL
  &categories=DELUXE
  &bedTypes=KING
  &page=0&size=10
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
Accept: application/json
```

This is the exact JWT that AuthService just generated and stored in
memory. Handing to TLS.

---

**TLS Layer (Client):** Encrypting search request with existing session
keys. Handing to TCP/IP.

---

**TCP/IP (Client OS):** Packets to Cloudflare. ~3ms.

---

**CDN (Cloudflare):** TLS termination.
```
GET /api/v1/hotels/search?cityId=550e8400...
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
```

Cache miss (authenticated request). Forwarding to API Gateway.

---

**API Gateway:**

**Rate limiting:**
```
Key:    rate_limit:user:f47ac10b...:search
Count:  1 request
Limit:  30 per minute
Status: PASS ✅
```

**JWT expiry check:**
```
Decoding: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
exp:      1703778923  (2024-12-19T10:55:23Z)
now:      1703692583  (2024-12-18T10:56:23Z — 1 minute after login)
Valid: ✅
```

Routing to miniagoda-app:8080. Forwarding.

---

**Load Balancer → Nginx → Tomcat.**

---

**HTTP Server (Tomcat):** Parsing GET request with Authorization header.
Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:** JWT present this time.

**Extract:**
```
Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
```

**Decode:**
```
Header:  { "alg": "HS256", "typ": "JWT" }
Payload: {
           "sub":   "f47ac10b-58cc-4372-a567-0e02b2c3d479",
           "role":  "GUEST",
           "email": "shawon@example.com",
           "iat":   1703692523,
           "exp":   1703778923
         }
```

**Verify signature:**
```
HMAC-SHA256(header + "." + payload, SECRET_KEY) → matches stored signature ✅
```

**Check expiry:**
```
exp: 1703778923 > now: 1703692583 ✅ — token is valid
```

**Populate SecurityContext:**
```java
SecurityContextHolder.getContext().setAuthentication(
    new UsernamePasswordAuthenticationToken(
        "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        null,
        [ROLE_GUEST]
    )
);
```

No database call needed — JWT is self-validating via its signature.
This is the key advantage of JWT: stateless authentication.

Passing to DispatcherServlet.

---

**DispatcherServlet:** Routing to HotelSearchController.searchByCity().

*(From here the conversation continues exactly as Search Scenario 1 —
hotels fetched, availability checked, prices resolved, results returned.)*

*(Full detail in: docs/flows/search/search-logged-in-full-results.md)*

---

**Shawon:** I can see the Bangkok hotels. The login worked, and my search
is showing personalized results with my search history being recorded.

---

## Token Lifecycle Summary

```
Login request
  ↓
AuthService generates:
  Access Token  — valid 24 hours, stored in Browser memory
  Refresh Token — valid 30 days, stored in TokenRepository + Browser

Every authenticated request:
  Browser sends: Authorization: Bearer {accessToken}
  Spring Security validates signature + expiry
  No database call — pure cryptographic verification

Access token expires after 24 hours:
  Browser sends refresh token to POST /api/v1/auth/refresh
  AuthService validates refresh token against TokenRepository
  New access token issued — refresh token may be rotated

Logout:
  POST /api/v1/auth/logout
  AuthService marks refresh token as used=true in TokenRepository
  Browser clears access token from memory
  Future refresh attempts rejected
```

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Submits login form |
| 2 | Browser | HTTP POST — email + password, no JWT |
| 3 | TLS (Client) | Encrypt credentials |
| 4–8 | Network layers | CDN → Gateway → LB → Nginx → Tomcat |
| 9 | Spring Security | No JWT → public endpoint |
| 10 | AuthController | Deserialize → AuthRequest |
| 11 | AuthService | Look up user by email |
| 12 | UserRepository | SELECT WHERE email=? |
| 13 | PostgreSQL | User found — ACTIVE, hashed password returned |
| 14 | AuthService | status=ACTIVE ✅ |
| 15 | AuthService | BCrypt verify — ~200ms deliberately slow |
| 16 | JwtUtil | Generate access token (24h expiry) |
| 17 | JwtUtil | Generate refresh token (30d expiry) |
| 18 | TokenRepository | INSERT refresh token into tokens table |
| 19 | PostgreSQL | Token stored |
| 20 | AuthController | 200 OK with token pair |
| 21 | Return path | Response back through all layers |
| 22 | Browser | Store tokens — accessToken in memory |
| 23 | Shawon | Logged in — immediately searches |
| 24 | Browser | GET /hotels/search with Authorization: Bearer {token} |
| 25 | Spring Security | JWT signature verified — NO database call |
| 26 | SecurityContext | Populated with Shawon's userId and GUEST role |
| 27 | HotelSearchService | Proceeds as Search Scenario 1 |