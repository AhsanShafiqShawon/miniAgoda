# Scenario 1: Successful Registration

**User:** Shawon (new user, not yet registered)
**Action:** Fills registration form and submits
**Outcome:** Account created with INACTIVE status, verification email sent async

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The new user |
| `Browser` | Client | Builds and sends HTTP POST |
| `TLS (Client)` | Client OS | Encrypts outgoing data |
| `TCP/IP (Client)` | Client OS | Breaks into packets, routes |
| `Internet` | Network | Routes packets |
| `CDN (Cloudflare)` | Edge | TLS termination, no caching for POST |
| `API Gateway` | Infrastructure | Rate limiting, routing |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `Reverse Proxy (Nginx)` | Infrastructure | HTTP parsing, routing |
| `TCP/IP (Server)` | Server OS | Receives packets, reassembles |
| `HTTP Server (Tomcat)` | Server | Parses HTTP, hands to Spring |
| `DispatcherServlet` | Spring | Routes to correct handler |
| `Spring Security Filter` | Spring | No JWT — public endpoint |
| `UserController` | Application | Maps HTTP to service call |
| `UserService` | Domain | Validates and creates user |
| `RegisterRequest` | Domain | Carries registration data |
| `UserRepository` | Data | Checks email uniqueness, saves user |
| `PostgreSQL` | Database | Stores user record |
| `AuthService` | Domain | Generates verification token |
| `TokenRepository` | Data | Stores verification token |
| `NotificationService` | Domain | Sends verification email async |
| `NotificationRepository` | Data | Persists notification record |
| `EmailGateway` | Infrastructure | Sends actual email via SMTP |

---

## The Conversation

---

**Shawon:** I want to create an account on miniAgoda.
*(fills the registration form)*

```
First Name:          Ahsan Shafiq
Last Name:           Shawon
Email:               shawon@example.com
Password:            MySecurePass123!
Preferred Currency:  BDT
Phone:               +880 1712345678
```

*(clicks Register)*

---

**Browser:** Building HTTP POST request. This contains sensitive data —
password must never travel in plain text.

```
POST /api/v1/auth/register
HTTP/1.1
Host: api.miniagoda.com
Content-Type: application/json
Accept: application/json
User-Agent: Mozilla/5.0
Content-Length: 187

{
  "firstName":         "Ahsan Shafiq",
  "lastName":          "Shawon",
  "email":             "shawon@example.com",
  "password":          "MySecurePass123!",
  "preferredCurrency": "BDT",
  "phoneNumber": {
    "countryCode": "+880",
    "number":      "1712345678"
  }
}
```

No Authorization header — Shawon has no account yet. Handing to TLS.

---

**TLS Layer (Client):** Received HTTP POST with sensitive payload.

Checking session — no existing TLS session with api.miniagoda.com on
this device. Performing TLS 1.3 handshake:

```
1. ClientHello →
   Supported versions:  TLS 1.3
   Cipher suites:       TLS_AES_256_GCM_SHA384
   Key share:           x25519 public key

2. ← ServerHello
   Selected version:    TLS 1.3
   Selected cipher:     TLS_AES_256_GCM_SHA384
   Key share:           server x25519 public key
   Certificate:         api.miniagoda.com (verified ✅)

3. Key derivation:
   Shared secret computed via x25519 ECDH
   Session keys derived from shared secret

4. Handshake complete — encrypting payload
```

Encrypting POST body with AES-256-GCM:
```
[TLS Record]
Content Type: Application Data (23)
Length:       215 bytes

[Encrypted Payload]
7f3a2b1c4d5e... (215 bytes — password now completely opaque)

[Authentication Tag]
e9f8a7b6c5d4...
```

Handing to TCP/IP.

---

**TCP/IP (Client OS):** Received encrypted bytes.

```
Packet 1:
  Source IP:        192.168.1.105
  Destination IP:   104.21.45.23   (Cloudflare Bangkok)
  Source Port:      55123
  Destination Port: 443
  Sequence No:      1001
  Flags:            PSH, ACK
  Payload:          TLS record bytes
```

Routing to Cloudflare via home router → ISP → Cloudflare PoP.

---

**Internet:** Routing packets. Bangkok → Cloudflare Bangkok edge. ~3ms.

---

**CDN (Cloudflare):** Received packets.

**Step 1 — TLS Termination:**
```
Decrypted:
POST /api/v1/auth/register
Content-Type: application/json

{
  "firstName": "Ahsan Shafiq",
  "lastName":  "Shawon",
  "email":     "shawon@example.com",
  "password":  "MySecurePass123!",
  ...
}
```

**Step 2 — Cache check:**
POST request — never cached. Passing through.

**Step 3 — DDoS protection:**
Registration endpoint — checking for bot patterns:
```
Request rate from 192.168.1.105: 1 registration attempt
Threshold: 5 attempts per hour per IP
Status: PASS ✅
```

**Step 4 — Forward:**
```
POST /api/v1/auth/register
Host:            api-gateway.internal
X-Forwarded-For: 192.168.1.105
CF-Ray:          1a2b3c4d5e6f7g8h-BKK
Content-Type:    application/json
```

Re-encrypting with internal TLS and forwarding to API Gateway.

---

**API Gateway:** Received POST request.

**Rate limiting:**
```
Key:    rate_limit:ip:192.168.1.105:register
Count:  1 attempt in last 3600 seconds
Limit:  5 registrations per hour per IP
Status: PASS ✅
```

**JWT check:**
No Authorization header. `/api/v1/auth/register` is a public endpoint —
no JWT required.

**Routing:**
```
Path:    /api/v1/auth/register
Method:  POST
Routes to: miniagoda-app:8080
```

Forwarding with:
```
X-Request-ID: req-1a2b3c4d-5e6f-7g8h-9i0j
```

---

**Load Balancer:**
```
Instance 1: miniagoda-app-1:8080 — connections: 31
Instance 2: miniagoda-app-2:8080 — connections: 38
Instance 3: miniagoda-app-3:8080 — connections: 29

Selected: miniagoda-app-3:8080 (least connections)
```

---

**Reverse Proxy (Nginx):**
```nginx
location /api/v1/auth/ {
    proxy_pass         http://miniagoda-app-3:8080;
    proxy_set_header   X-Real-IP $remote_addr;
    proxy_read_timeout 30s;
}
```

Forwarding to Tomcat.

---

**TCP/IP (Server OS):** Reassembling packets. Placing HTTP stream in
Tomcat socket buffer on app-3.

---

**HTTP Server (Tomcat):** Thread picked up request.

Parsing:
```
Method:       POST
URI:          /api/v1/auth/register
Content-Type: application/json
Body:         { "firstName": "Ahsan Shafiq", ... }
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:** Checking for JWT:
```
Authorization header: NOT PRESENT
```

Endpoint `/api/v1/auth/register` is `@PreAuthorize("permitAll()")`.
No authentication required. Setting AnonymousAuthenticationToken.
Passing to DispatcherServlet.

---

**DispatcherServlet:** Routing:
```
POST /api/v1/auth/register
→ UserController.register()
  @PostMapping("/api/v1/auth/register")
  @PreAuthorize("permitAll()")
```

Invoking UserController.

---

**UserController:** Received request.

Deserializing JSON body into `RegisterRequest`:
```java
RegisterRequest request = new RegisterRequest(
    firstName:         "Ahsan Shafiq",
    lastName:          "Shawon",
    email:             "shawon@example.com",
    password:          "MySecurePass123!",
    phoneNumber:       PhoneNumber("+880", "1712345678"),
    preferredCurrency: "BDT"
);
```

Running `@Valid` annotations:
```
firstName:         not blank ✅
lastName:          not blank ✅
email:             valid format ✅
password:          not blank ✅
preferredCurrency: 3 characters ✅
phoneNumber:       countryCode starts with + ✅
```

Bean validation passed. Calling UserService.registerUser(request).

---

**UserService:** Received RegisterRequest.

**Step 1 — Validate email uniqueness:**
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
0

No existing user with this email.
```

---

**UserRepository:** Count = 0. Email is unique. Returning to UserService.

---

**UserService:** Email is free.

**Step 2 — Hash password:**
```
Algorithm:  BCrypt
Rounds:     12
Input:      "MySecurePass123!"
Output:     "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh"
```

Password is now irreversibly hashed. The plain text "MySecurePass123!"
is discarded from memory immediately.

**Step 3 — Build User record:**
```java
User user = new User(
    id:                UUID.randomUUID(),
                       // "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    firstName:         "Ahsan Shafiq",
    lastName:          "Shawon",
    email:             "shawon@example.com",
    password:          "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh",
    phoneNumber:       PhoneNumber("+880", "1712345678"),
    primaryImageId:    null,
    preferredCurrency: "BDT",
    role:              UserRole.GUEST,      // default
    status:            UserStatus.INACTIVE, // default — pending verification
    createdAt:         LocalDateTime.now(),
    updatedAt:         LocalDateTime.now()
);
```

**Step 4 — Save user:**
UserRepository, save this user.

---

**UserRepository:** Generating SQL:
```sql
INSERT INTO users (
    id, first_name, last_name, email, password,
    phone_country_code, phone_number,
    primary_image_id, preferred_currency,
    role, status, created_at, updated_at
) VALUES (
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'Ahsan Shafiq',
    'Shawon',
    'shawon@example.com',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh',
    '+880',
    '1712345678',
    NULL,
    'BDT',
    'GUEST',
    'INACTIVE',
    '2024-12-18T10:15:23Z',
    '2024-12-18T10:15:23Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(users table)*
```
Parse:   ✅
Bind:    ✅
Execute: row inserted ✅
WAL:     write-ahead log updated ✅
Result:  INSERT 0 1
```

---

**UserRepository:** Insert confirmed. Returning saved User to UserService.

---

**UserService:** User saved with status INACTIVE.

**Step 5 — Trigger email verification:**
AuthService, Shawon just registered. Generate a verification token and
send the email.

---

**AuthService:** Received request to verify Shawon's email.

**Step 1 — Generate verification token:**
```java
String token = UUID.randomUUID().toString();
// "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"

LocalDateTime expiry = LocalDateTime.now().plusHours(24);
// "2024-12-19T10:15:23Z"
```

**Step 2 — Store token:**
TokenRepository, save this verification token.

---

**TokenRepository:** Generating SQL:
```sql
INSERT INTO tokens (
    id, user_id, token, type, expires_at, used, created_at
) VALUES (
    UUID.randomUUID(),
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    'EMAIL_VERIFICATION',
    '2024-12-19T10:15:23Z',
    false,
    '2024-12-18T10:15:23Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(tokens table)*
```
INSERT 0 1 ✅
```

---

**TokenRepository:** Token saved. Returning to AuthService.

---

**AuthService:** Token stored. Now building verification URL:
```
https://miniagoda.com/verify-email?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
```

NotificationService, send a verification email to shawon@example.com
with this link. Do it asynchronously — don't block registration.

---

**NotificationService:** *(on @Async thread)*

Received request. Building Notification record:
```java
Notification notification = new Notification(
    id:           UUID.randomUUID(),
    userId:       "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    hotelId:      null,
    type:         NotificationType.EMAIL_VERIFICATION,
    subject:      "Verify your miniAgoda account",
    body:         """
                  Hi Ahsan Shafiq,

                  Welcome to miniAgoda! Please verify your email address
                  by clicking the link below:

                  https://miniagoda.com/verify-email?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d

                  This link expires in 24 hours.

                  If you did not create an account, please ignore this email.

                  The miniAgoda Team
                  """,
    channel:      Channel.EMAIL,
    status:       NotificationStatus.PENDING,
    readStatus:   NotificationReadStatus.UNREAD,
    scheduledAt:  null,    // send immediately
    sentAt:       null,
    createdAt:    LocalDateTime.now(),
    updatedAt:    LocalDateTime.now()
);
```

NotificationRepository, save this.

---

**NotificationRepository:** Generating SQL:
```sql
INSERT INTO notifications (
    id, user_id, hotel_id, type, subject, body,
    channel, status, read_status,
    scheduled_at, sent_at, created_at, updated_at
) VALUES (
    'c3d4e5f6-g7h8-9012-cdef-g12345678902',
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    NULL,
    'EMAIL_VERIFICATION',
    'Verify your miniAgoda account',
    'Hi Ahsan Shafiq, Welcome to miniAgoda!...',
    'EMAIL',
    'PENDING',
    'UNREAD',
    NULL,
    NULL,
    '2024-12-18T10:15:24Z',
    '2024-12-18T10:15:24Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(notifications table)*
```
INSERT 0 1 ✅
```

---

**NotificationRepository:** Saved. Returning to NotificationService.

---

**NotificationService:** Record saved as PENDING. Now sending via
EmailGateway.

EmailGateway, send this email:
```
To:      shawon@example.com
Subject: Verify your miniAgoda account
Body:    Hi Ahsan Shafiq, Welcome to miniAgoda!...
         https://miniagoda.com/verify-email?token=9b1deb4d...
```

---

**EmailGateway:** *(SmtpEmailGateway implementation)*

Connecting to SMTP server:
```
Host:       smtp.miniagoda.com
Port:       587
Security:   STARTTLS
Auth:       miniagoda-noreply / [app password]
```

Building MIME message:
```
From:     noreply@miniagoda.com
To:       shawon@example.com
Subject:  Verify your miniAgoda account
MIME:     multipart/alternative
  text/plain: Hi Ahsan Shafiq, Welcome to miniAgoda!...
  text/html:  <h1>Welcome!</h1><p>Click to verify...</p>
```

Sending via SMTP:
```
EHLO miniagoda.com
AUTH LOGIN [credentials]
MAIL FROM: <noreply@miniagoda.com>
RCPT TO: <shawon@example.com>
DATA
  [MIME message]
.
250 OK: Message queued as 1a2b3c4d
```

Email accepted by SMTP server. Returning success to NotificationService.

---

**NotificationService:** Email sent successfully.

Updating notification status:
```sql
UPDATE notifications
SET    status   = 'SENT',
       sent_at  = '2024-12-18T10:15:25Z',
       updated_at = '2024-12-18T10:15:25Z'
WHERE  id = 'c3d4e5f6-g7h8-9012-cdef-g12345678902';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(notifications table)*
```
UPDATE 1 ✅
```

---

**NotificationService:** Status updated to SENT. Async work complete.
*(thread returns to pool)*

---

**AuthService:** NotificationService is handling email asynchronously.
Returning to UserService.

---

**UserService:** Email verification triggered. Returning created User
to UserController.

---

**UserController:** Received created User. Shawon's password must
NOT be in the response. Building response DTO:

```java
UserResponse response = new UserResponse(
    id:                "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    firstName:         "Ahsan Shafiq",
    lastName:          "Shawon",
    email:             "shawon@example.com",
    preferredCurrency: "BDT",
    role:              "GUEST",
    status:            "INACTIVE"
    // password field deliberately excluded
);
```

Serializing to JSON:
```json
HTTP/1.1 201 Created
Content-Type: application/json
X-Request-ID: req-1a2b3c4d-5e6f-7g8h-9i0j
Location: /api/v1/users/f47ac10b-58cc-4372-a567-0e02b2c3d479

{
  "id":                "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "firstName":         "Ahsan Shafiq",
  "lastName":          "Shawon",
  "email":             "shawon@example.com",
  "preferredCurrency": "BDT",
  "role":              "GUEST",
  "status":            "INACTIVE",
  "message":           "Registration successful. Please check your email to verify your account."
}
```

Note: HTTP 201 Created — not 200 OK. A new resource was created.
Location header points to the new user resource.

---

**DispatcherServlet:** Writing 201 response to HttpServletResponse.
Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing to bytes. Placing in socket buffer.

---

**TCP/IP (Server OS):** Breaking into packets. Sending through Nginx.

---

**Reverse Proxy (Nginx):** Adding:
```
X-Served-By:     miniagoda-app-3
X-Response-Time: 234ms
```
Forwarding to Load Balancer.

---

**Load Balancer:** Decrementing connection count for app-3.
Forwarding to API Gateway.

---

**API Gateway:** Logging:
```
Method:   POST /api/v1/auth/register
User:     anonymous (new registration)
Status:   201 Created
Duration: 241ms
```
Forwarding to Cloudflare.

---

**CDN (Cloudflare):** POST response — never cached. Forwarding to
Shawon with TLS encryption.

---

**TLS Layer (Client):** Decrypting response. Handing to Browser.

---

**Browser:** Received 201 response. Parsing JSON and rendering:

```
✅ Registration Successful!

Welcome to miniAgoda, Ahsan Shafiq!

We have sent a verification email to shawon@example.com.
Please check your inbox and click the link to activate your account.

[Resend verification email]
```

---

**Shawon:** I can see the success message. Let me check my email...

*(Meanwhile, in Shawon's inbox)*

```
From:    noreply@miniagoda.com
To:      shawon@example.com
Subject: Verify your miniAgoda account

Hi Ahsan Shafiq,

Welcome to miniAgoda! Please verify your email address
by clicking the link below:

https://miniagoda.com/verify-email?token=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d

This link expires in 24 hours.

The miniAgoda Team
```

*(Email verification flow continues in registration-email-verification.md)*

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Fills and submits registration form |
| 2 | Browser | HTTP POST — no Authorization header |
| 3 | TLS (Client) | TLS 1.3 handshake + encrypt body |
| 4 | TCP/IP (Client) | Packets to Cloudflare |
| 5 | Internet | Route to Cloudflare Bangkok |
| 6 | CDN | TLS terminate, bot check, forward |
| 7 | API Gateway | Rate limit by IP ✅, public endpoint |
| 8 | Load Balancer | Select app-3 |
| 9 | Nginx | Forward to Tomcat |
| 10 | Tomcat | Parse POST body |
| 11 | Spring Security | No JWT → AnonymousAuthenticationToken |
| 12 | DispatcherServlet | Route to UserController |
| 13 | UserController | Deserialize JSON → RegisterRequest, @Valid |
| 14 | UserService | Check email uniqueness |
| 15 | UserRepository | SELECT COUNT(*) WHERE email=? |
| 16 | PostgreSQL | Count = 0 — email free |
| 17 | UserService | BCrypt hash password (12 rounds) |
| 18 | UserService | Build User — status=INACTIVE, role=GUEST |
| 19 | UserRepository | INSERT INTO users |
| 20 | PostgreSQL | User row committed |
| 21 | UserService | Trigger email verification |
| 22 | AuthService | Generate UUID verification token |
| 23 | TokenRepository | INSERT INTO tokens |
| 24 | PostgreSQL | Token row committed |
| 25 | AuthService | Build verification URL |
| 26 | NotificationService | Build PENDING notification (@Async) |
| 27 | NotificationRepository | INSERT INTO notifications |
| 28 | PostgreSQL | Notification row committed |
| 29 | EmailGateway | SMTP send — 250 OK |
| 30 | NotificationService | UPDATE status=SENT |
| 31 | PostgreSQL | Notification row updated |
| 32 | UserController | Build response — password excluded |
| 33 | Return path | 201 Created travels back through all layers |
| 34 | Browser | Renders success message |
| 35 | Shawon | Receives verification email in inbox |