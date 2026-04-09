# JwtBeanConfig — Code Prose

`com.miniagoda.common.config.JwtBeanConfig`

---

## Overview

This class constructs and registers the two beans the application needs to issue and verify JWTs: an encoder and a decoder.

It is annotated with `@Configuration`, making it a source of bean definitions rather than a regular component. Both beans it produces are backed by Nimbus JOSE+JWT, the library Spring Security delegates to under the hood for JWT operations. Neither `JwtUtil` nor `JwtAuthenticationFilter` know about Nimbus directly — they depend on Spring's `JwtEncoder` and `JwtDecoder` abstractions and receive the Nimbus implementations through injection.

The class depends on `JwtConfig`, injected through the constructor, which supplies the raw secret string the signing key is derived from.

---

## `secretKey()`

This private method is the shared foundation both beans are built on.

It reads the secret string from `JwtConfig`, encodes it to bytes using UTF-8, and wraps it in a `SecretKeySpec` configured for the `HmacSHA256` algorithm. The result is the cryptographic key that will be used to sign outgoing tokens and verify incoming ones.

The method is private because the key material has no business being accessible outside this class. It is called separately by each bean method rather than cached as a field, which is a minor point of style — since both beans are singletons instantiated once at startup, the difference is inconsequential in practice.

---

## `jwtDecoder()`

This bean method produces the `JwtDecoder` that `JwtAuthenticationFilter` uses to verify incoming tokens.

It builds a `NimbusJwtDecoder` configured with the shared secret key. When the filter calls `jwtDecoder.decode(token)`, Nimbus recomputes the token's HMAC signature using this key and compares it to the signature embedded in the token. If they match and the token has not expired, decoding succeeds. If either check fails, a `JwtException` is thrown — which is exactly what the filter catches and converts into a `401` response.

---

## `jwtEncoder()`

This bean method produces the `JwtEncoder` that `JwtUtil` uses to mint new tokens.

It constructs a `NimbusJwtEncoder` backed by an `ImmutableSecret` wrapping the same key. `ImmutableSecret` is Nimbus's representation of a static, symmetric secret — one that does not rotate and is not fetched from an external source. When `JwtUtil` calls `jwtEncoder.encode(parameters)`, Nimbus signs the claims set with this key using HMAC-SHA256 and returns the compact token string.

The encoder and decoder are two sides of the same key. A token the encoder produces can be verified by the decoder because they share the same secret — which is what makes symmetric signing work, and also why the secrecy of that value matters so much.

# JwtBeanConfig — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **locksmith** that cuts the key and hands out the tools that use it.

`JwtConfig` holds the secret string from the config file. But a raw string isn't a cryptographic key — it's just text. `JwtBeanConfig` takes that string, converts it into an actual key, and uses it to build two things: an encoder that signs tokens, and a decoder that validates them. Both tools are registered as Spring beans so the rest of the app can use them.

| Concept | What it is |
|---|---|
| `JwtBeanConfig` | Builds and registers the JWT encoder and decoder beans |
| `secretKey()` | Converts the raw secret string into a real cryptographic key |
| `JwtEncoder` | Signs and builds JWT tokens — used by `JwtUtil` |
| `JwtDecoder` | Validates and unpacks JWT tokens — used by `JwtAuthenticationFilter` |

---

## 🧩 Step-by-Step in Plain Terms

### 1. The constructor — one dependency comes in

```java
public JwtBeanConfig(JwtConfig jwtConfig) {
    this.jwtConfig = jwtConfig;
}
```

`JwtConfig` is injected by Spring. This class needs it for one reason — to read the secret string and turn it into a usable key. Everything else flows from that.

---

### 2. `secretKey()` — the private method everything depends on

```java
private SecretKeySpec secretKey() {
    byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
    return new SecretKeySpec(keyBytes, "HmacSHA256");
}
```

This is the most important method in the class, even though it's private and short.

**Step 1 — Convert the string to bytes**

```java
byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
```

Cryptographic operations work on raw bytes, not strings. The secret string — something like `"my-super-secret-key"` — is converted to a byte array using UTF-8 encoding. `StandardCharsets.UTF_8` is used explicitly rather than relying on the system default, so the result is the same on every machine regardless of the OS or locale.

**Step 2 — Wrap it in a `SecretKeySpec`**

```java
return new SecretKeySpec(keyBytes, "HmacSHA256");
```

`SecretKeySpec` is Java's standard way of wrapping raw bytes into a formal `SecretKey` object. The second argument — `"HmacSHA256"` — declares the algorithm this key is intended for. HMAC-SHA256 is a symmetric signing algorithm — the same key is used to both sign and verify. That means whoever holds the secret can both create tokens and validate them.

---

### 3. `jwtDecoder()` — the token validator bean

```java
@Bean
public JwtDecoder jwtDecoder() {
    SecretKeySpec key = secretKey();
    return NimbusJwtDecoder.withSecretKey(key).build();
}
```

`NimbusJwtDecoder` is the Nimbus library's implementation of `JwtDecoder`. It's built with the secret key so it can verify the HMAC-SHA256 signature on every incoming token.

When `JwtAuthenticationFilter` calls `jwtDecoder.decode(token)`, this is the object doing the work. It checks the signature, checks the expiry, and unpacks the claims. If anything is wrong, it throws a `JwtException`.

---

### 4. `jwtEncoder()` — the token builder bean

```java
@Bean
public JwtEncoder jwtEncoder() {
    SecretKeySpec key = secretKey();
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
}
```

`NimbusJwtEncoder` is the Nimbus library's implementation of `JwtEncoder`. It's wrapped around an `ImmutableSecret` — a Nimbus class that holds a key that never changes at runtime. The encoder uses it to sign every token `JwtUtil` builds.

`ImmutableSecret` signals to Nimbus that this is a static, fixed key — not a rotating key set. For a single-secret HMAC setup, this is the correct wrapper.

---

### 5. Why `secretKey()` is called twice — once for each bean

You might notice `secretKey()` is called separately inside `jwtDecoder()` and `jwtEncoder()` rather than being called once and shared. This is fine because `SecretKeySpec` is a lightweight, stateless value object. Creating it twice produces two identical keys — there's no cost or risk to doing so. Both beans end up signing and verifying with the same underlying bytes.

---

## 🔥 One-Line Summary

> This class takes the raw secret string from `JwtConfig`, converts it into a real cryptographic key, and uses it to build the encoder that signs tokens and the decoder that validates them.

---

## 💡 Deep Dive: What is HMAC-SHA256 and why does it matter here?

JWT tokens can be signed using different algorithms. The two main families are:

| Algorithm family | How it works | Key type |
|---|---|---|
| HMAC (symmetric) | Same key signs and verifies | One shared secret |
| RSA / EC (asymmetric) | Private key signs, public key verifies | Key pair |

This app uses HMAC-SHA256 — the symmetric approach.

---

### What HMAC-SHA256 actually does

When `JwtEncoder` signs a token, it runs the header and payload through SHA-256 using the secret key as input. The result is a short, fixed-length signature that gets appended to the token.

When `JwtDecoder` validates a token, it does the same calculation on the incoming header and payload and compares the result to the signature already on the token. If they match — the token is genuine. If they don't — something was tampered with, or the wrong key was used.

```
Token arrives:   header.payload.signature
Decoder runs:    HMAC-SHA256(header.payload, secret) → expected_signature
Comparison:      expected_signature == signature?  ✅ valid  ❌ reject
```

---

### Symmetric vs asymmetric — when does asymmetric matter?

HMAC is fine here because only one service is both issuing and validating tokens. The same app that creates the token also checks it.

Asymmetric signing (RSA/EC) becomes necessary when multiple services need to validate tokens but shouldn't be trusted to create them. For example, a dedicated auth service signs with a private key, and ten downstream microservices verify with the public key — but none of them can forge a token because they don't have the private key.

For a single application like this, HMAC-SHA256 is simpler and equally secure.

---

### Why the secret string must be long enough

HMAC-SHA256 uses a 256-bit key internally. If the secret string is shorter than 32 characters, the effective key space is smaller than the algorithm assumes — weaker than intended. A secret like `"secret"` is technically valid but dangerously short. In production the secret should be a long, randomly generated string — at least 32 characters, ideally 64 or more.

---

### How the three JWT classes connect

```
JwtConfig
    └── holds the raw secret string
            ↓
JwtBeanConfig
    └── secretKey() converts it to SecretKeySpec
            ├── JwtEncoder bean  → handed to JwtUtil
            └── JwtDecoder bean  → handed to JwtAuthenticationFilter
                    ↓                           ↓
             Signs new tokens           Validates incoming tokens
```

`JwtConfig` owns the secret. `JwtBeanConfig` converts it into tools. `JwtUtil` uses the encoder to build tokens. `JwtAuthenticationFilter` uses the decoder to check them. Each class does exactly one thing and hands off to the next.

---

### The analogy

| Thing | Analogy |
|---|---|
| Secret string in `JwtConfig` | The raw metal blank — the material a key is cut from |
| `secretKey()` | The locksmith cutting the actual key from the blank |
| `JwtEncoder` | A stamp with that key — used to mark every token as genuine |
| `JwtDecoder` | A reader that checks incoming stamps against the same key |
| `ImmutableSecret` | A locked box the key sits in — it goes in once and never changes |
| HMAC-SHA256 | The pattern cut into the key — both the stamp and the reader must match it exactly |

---

### Final one-liner

> The secret string is just text until `secretKey()` turns it into a real cryptographic key — after that, the encoder uses it to stamp tokens as genuine and the decoder uses it to verify they haven't been faked or tampered with.