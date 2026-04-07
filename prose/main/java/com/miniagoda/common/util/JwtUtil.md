# JwtUtil — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **token factory**.

Whenever a user logs in successfully, something needs to actually create the JWT tokens — stamp them with the user's identity, set an expiry time, and sign them. That is exactly what `JwtUtil` does.

| Concept | What it is |
|---|---|
| `JwtUtil` | The class responsible for building and issuing JWT tokens |
| `generateAccessToken` | Makes the short-lived token used for API requests |
| `generateRefreshToken` | Makes the long-lived token used to get new access tokens |
| `buildToken` | The internal method that actually constructs the token |

---

## 🧩 Step-by-Step in Plain Terms

### 1. The constructor — two dependencies come in

```java
public JwtUtil(JwtEncoder jwtEncoder, JwtConfig jwtConfig) {
```

Spring injects two things when this class is created:

**`JwtEncoder`** — this is the signing machine. It takes a set of claims and cryptographically signs them using the secret key from `JwtConfig`. You don't call it directly — you pass it your claims and it hands back a signed token.

**`JwtConfig`** — this is the settings card from earlier. `JwtUtil` reads the expiry times from it so it knows how long to make each token last.

---

### 2. `generateAccessToken(String userId, String role)`

The caller passes in a user ID and a role. This method's only job is to say:

> *"Make me a token for this user, and use the access token expiry time."*

It delegates immediately to `buildToken`, passing the access expiry from `JwtConfig`. Nothing else happens here.

---

### 3. `generateRefreshToken(String userId, String role)`

Identical in shape to `generateAccessToken`, but passes the refresh token expiry instead.

> *"Make me a token for this user, and use the refresh token expiry time."*

Same user, same role — just a longer-lived token for a different purpose.

---

### 4. `buildToken(String userId, String role, long expiryMs)` — where the real work happens

This is the private method both public methods delegate to. It does four things in sequence.

**Step 1 — Capture the current moment**
```java
Instant now = Instant.now();
Instant expiry = now.plusMillis(expiryMs);
```
It records exactly when the token is being created, then calculates when it should expire by adding the expiry duration on top. Both timestamps will be baked into the token itself.

**Step 2 — Build the claims**
```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("miniagoda")
        .issuedAt(now)
        .expiresAt(expiry)
        .subject(userId)
        .claim("role", role)
        .build();
```
Claims are the payload of the token — the information stamped inside it. Think of them like the fields printed on an ID card.

| Claim | What it means |
|---|---|
| `issuer` | Who created this token — `"miniagoda"` |
| `issuedAt` | When it was created |
| `expiresAt` | When it stops being valid |
| `subject` | Who this token belongs to — the user's ID |
| `role` | What kind of user this is (guest, host, admin) |

**Step 3 — Sign and encode**
```java
return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
```
The claims are handed to `JwtEncoder`, which signs them using the secret key and produces the final token string — the thing that looks like `eyJhbGci...`. That string is returned to the caller.

---

## 🔥 One-Line Summary

> This class takes a user ID and role, stamps them into a JWT with an expiry time, signs it, and returns the token string.

---

## 💡 Deep Dive: What is actually inside a JWT?

A JWT looks like gibberish — three Base64 chunks separated by dots:

```
eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJtaW5pYWdvZGEi....<signature>
```

But it's not encrypted. Anyone can decode the first two parts and read them. The three parts are:

| Part | What it contains |
|---|---|
| Header | Algorithm used to sign the token |
| Payload | The claims — user ID, role, expiry, issuer |
| Signature | Proof that the token hasn't been tampered with |

---

### The signature is what matters

When `JwtEncoder` signs the token, it uses the secret key to produce a signature over the header and payload. When another service later tries to validate the token, it runs the same process and checks if the signature matches.

If someone tampers with the payload — changes the role from `GUEST` to `ADMIN`, for example — the signature no longer matches. The token is rejected.

> The payload is readable. The signature is what makes it trustworthy.

---

### Why two tokens — access and refresh?

| Token | Lifespan | Purpose |
|---|---|---|
| Access token | Short (e.g. 15 minutes) | Sent with every API request to prove identity |
| Refresh token | Long (e.g. 7 days) | Used only to get a new access token when the old one expires |

The access token is short-lived on purpose. If it gets stolen, it stops working in minutes. The refresh token lives longer but is used rarely and stored more carefully — typically in an `HttpOnly` cookie so JavaScript can't touch it.

---

### The analogy

| Thing | Analogy |
|---|---|
| Access token | A day pass to the venue — expires tonight |
| Refresh token | A membership card — use it at the desk to get a new day pass |
| Secret key | The stamp only the venue owns — fakes are instantly spotted |
| Tampering with claims | Scratching out "GUEST" on your pass and writing "VIP" — the bouncer checks the stamp and turns you away |

---

### Final one-liner

> A JWT's payload is readable by anyone, but the signature makes sure nobody can change it without being caught.