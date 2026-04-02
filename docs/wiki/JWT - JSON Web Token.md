# JWT — JSON Web Token

## The Problem: HTTP is Forgetful

HTTP has no memory. Every request is a stranger.

When a user logs in, the server checks their password and says "yes, this
is Shawon." But the moment that response is sent, the server forgets her
completely. The next request — even one millisecond later — arrives with
no context. The server has no idea who sent it.

So how does every request after login *prove* who it's from?

---

## The Naive Solution: Send Password Every Time

The user could send their email and password with every single request.

```
GET /api/v1/bookings
email: shawon@email.com
password: hunter2
```

This works but it's terrible. The password travels the wire constantly.
If any request is intercepted, the account is compromised. And the server
has to hit the database on every request to verify it.

---

## The Old Solution: Sessions

After login, the server creates a session — a record in memory or the
database:

```
session_id: abc123  →  user: shawon, role: GUEST, expires: 1hr
```

It sends `abc123` back to the browser as a [cookie](Cookie.md). The browser attaches
that cookie to every request automatically. The server looks up `abc123`,
finds the user, and proceeds.

This works, but the server must *store* every active session. With a
million logged-in users, that's a million session records. And if you have
multiple servers, they all need to share the same session store —
otherwise server 2 doesn't know about a session that server 1 created.

---

## The Modern Solution: JWT

Instead of the server remembering the user, what if the server gave the
user a *signed document* — a certificate that says "this is Shawon, she
is a GUEST, I the server vouch for this" — and the user presented that
document on every request?

The server doesn't store anything. It just checks that the document is
genuine.

That document is a **JWT — JSON Web Token**.

---

## What a JWT Looks Like

A JWT is a string of three Base64-encoded parts separated by dots:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaGF3b24iLCJyb2xlIjoiR1VFU1QiLCJpYXQiOjE3MDM2NTYwMDAsImV4cCI6MTcwMzc0MjQwMH0.abc123
```

Those three parts are:

```
HEADER . PAYLOAD . SIGNATURE
```

### Header

Decode it and you get:

```json
{
  "alg": "HS256"
}
```

Just says which algorithm was used to sign the token.

### Payload

Decode it and you get:

```json
{
  "sub":  "shawon-uuid-here",
  "role": "GUEST",
  "iat":  1703656000,
  "exp":  1703742400
}
```

This is the actual data — the user's ID, their role, when the token was
issued (`iat` = issued at), and when it expires (`exp`). These fields are
called **claims**.

### Signature

This is the part that makes it trustworthy:

```
HMAC-SHA256(
  base64(header) + "." + base64(payload),
  SECRET_KEY
)
```

The server takes the header and payload, runs them through a hashing
function using a secret key that **only the server knows**, and appends
the result.

If anyone tampers with the payload — say, changing `"role": "GUEST"` to
`"role": "ADMIN"` — the signature will no longer match, and the server
rejects it. The payload is readable by anyone (it's just Base64), but it
cannot be modified without invalidating the signature.

---

## The Login Flow

```
1. User sends email + password  →  POST /api/v1/auth/login

2. Server verifies password with BCrypt

3. Server creates a JWT:
   payload:   { sub: "shawon-uuid", role: "GUEST", exp: +24hrs }
   signs it:  HMAC-SHA256(payload, SECRET_KEY)
   sends it back to the user

4. Browser stores the JWT (memory or localStorage)

5. Every future request:
   GET /api/v1/bookings
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

6. Server receives the request
   - Decodes the JWT (no database lookup needed)
   - Verifies the signature (proves it wasn't tampered with)
   - Checks exp (proves it hasn't expired)
   - Reads sub and role from the payload
   - Proceeds knowing who the user is and what they're allowed to do
```

The server never stores the token. It just trusts the signature.

---

## The Problem JWT Introduces: You Can't Revoke It

Sessions are easy to invalidate — delete the session record and the user
is logged out instantly.

JWTs can't be "deleted" — the server doesn't store them. If a token is
stolen, or the user changes their password, or an admin bans the account
— the old JWT is still valid until it expires.

This is why access tokens have **short expiry times** — typically 15
minutes to 24 hours. The shorter the window, the smaller the damage if a
token is stolen.

---

## The Solution: Two Tokens

The standard approach uses two tokens, not one.

### Access Token
- Short-lived (e.g. 24 hours)
- Sent with every API request
- Verified by signature only — no database lookup
- If stolen, expires soon

### Refresh Token
- Long-lived (e.g. 7 days)
- Stored in the database
- Used for one purpose only — to get a new access token when the old one
  expires
- Because it lives in the database, it **can** be revoked

### The Two-Token Flow

```
Login
  → server issues access token (24hr) + refresh token (7 days)
  → refresh token saved to DB

Normal requests (first 24 hours)
  → send access token
  → server verifies signature — no DB hit

Access token expires
  → browser sends refresh token to POST /auth/refresh
  → server checks DB — is this refresh token still valid?
  → issues new access token (24hr)
  → old refresh token consumed, new one issued (rotation)

User changes password / admin bans account
  → server deletes refresh token from DB
  → next refresh attempt fails
  → user is effectively logged out within 24 hours at most
```

The refresh token is the one thing that gets persisted — it's the lever
that restores the ability to revoke sessions.

---

## Claims Reference

| Claim | Full Name | Meaning |
|---|---|---|
| `sub` | Subject | Who the token belongs to (usually user ID) |
| `iat` | Issued At | Unix timestamp of when the token was created |
| `exp` | Expiration | Unix timestamp of when the token stops being valid |
| `role` | — | Custom claim — user's role (GUEST, ADMIN, etc.) |

`sub`, `iat`, and `exp` are standard JWT claims defined in the spec.
Anything else (like `role`) is a custom claim you add yourself.

---

## Glossary

| Term | Meaning |
|---|---|
| JWT | JSON Web Token — a signed, self-contained document proving identity |
| Header | The first part — says which algorithm signed the token |
| Payload | The second part — the actual claims (user ID, role, expiry) |
| Signature | The third part — proves the payload wasn't tampered with |
| Claim | A key-value pair in the payload |
| Access token | Short-lived JWT sent with every API request |
| Refresh token | Long-lived token stored in DB, used only to renew access tokens |
| Bearer token | The HTTP pattern for sending a JWT — `Authorization: Bearer <token>` |
| SecurityContext | Spring's in-memory holder of "who is making this current request" |
| BCrypt | The hashing algorithm used to store passwords safely |
| HS256 | HMAC-SHA256 — the signing algorithm used to create the JWT signature |

---

## What Lives Where in a Spring Boot Project

```
JwtUtil.java          — generates tokens, verifies signatures, extracts claims
JwtConfig.java        — secret key + expiry durations, read from application.properties
SecurityConfig.java   — tells Spring which endpoints need a token and which don't
JwtFilter.java        — intercepts each request, validates the token, populates SecurityContext
RefreshToken.java     — entity stored in DB so refresh tokens can be revoked
AuthService.java      — login logic: verify password → issue both tokens
AuthController.java   — POST /auth/login, POST /auth/refresh, POST /auth/logout
```

---

## One-Paragraph Summary

A JWT is a signed document the server hands to the client after login.
The client attaches it to every request. The server verifies the
signature — proving the document is genuine and unmodified — and reads
the user's identity directly from the token without touching the
database. Access tokens are short-lived so stolen tokens expire quickly.
Refresh tokens are long-lived but stored in the database so they can be
revoked when needed — restoring the logout and ban capabilities that
pure JWTs lack.