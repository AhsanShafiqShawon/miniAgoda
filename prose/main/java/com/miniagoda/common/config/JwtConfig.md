# JwtConfig — Code Prose

`com.miniagoda.common.config.JwtConfig`

---

## Overview

This class has one job: hold the JWT settings the application needs to issue and validate tokens.

It is annotated with both `@Configuration` and `@ConfigurationProperties(prefix = "jwt")`. The first tells Spring to treat this class as a configuration source. The second tells Spring to look inside the application's property files for keys that start with `jwt.` — `jwt.secret`, `jwt.access-token-expiry-ms`, and so on — and bind their values directly onto the fields of this class. No manual parsing, no `@Value` annotations scattered across the codebase. Spring does the mapping once, at startup.

---

## `secret`

This field holds the signing key for every JWT the application produces.

When the application issues a token — after a user logs in, for example — it signs the token's payload with this secret using an HMAC algorithm. Any service that later receives that token can verify its authenticity by re-computing the signature with the same secret. If the signatures match, the token is genuine and untampered. If they don't, it is rejected.

The value itself never appears in source code. It is read from configuration at startup, which means it can be kept in an environment variable or a secrets manager and rotated without touching the application itself.

---

## `accessTokenExpiryMs`

This field controls how long a short-lived access token remains valid, expressed in milliseconds.

Access tokens are the credentials a client presents on every protected request. Because they are passed around frequently, keeping their lifespan short limits the damage if one is intercepted — an attacker holding a stolen access token only has a narrow window before it expires. A typical value might be fifteen minutes or an hour, though the exact figure is a product decision, not a hard rule.

---

## `refreshTokenExpiryMs`

This field controls how long a refresh token remains valid, also in milliseconds.

Refresh tokens exist precisely because access tokens are short-lived. When an access token expires, the client presents its refresh token to get a new one without asking the user to log in again. Refresh tokens are therefore longer-lived — days or weeks — but they are also more sensitive. They are typically stored more carefully than access tokens, issued only once per login, and invalidated explicitly on logout.

The separation of these two expiry values into distinct fields makes the tradeoff explicit: short access windows for safety, longer refresh windows for usability.

# JwtConfig — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class like a **settings card** for your JWT system.

Instead of hardcoding values like secret keys and expiry times directly in your code, you write them once in a config file — and this class reads them in and holds them for the rest of the app to use.

| Concept | What it is |
|---|---|
| `JwtConfig` | A container that holds JWT settings from your config file |
| `@ConfigurationProperties` | The mechanism that reads those settings in automatically |

---

## 🧩 Step-by-Step in Plain Terms

### 1. `@Configuration` + `@ConfigurationProperties(prefix = "jwt")`

These two annotations work together.

`@Configuration` tells Spring:
> *"This is a Spring-managed class. Keep it around."*

`@ConfigurationProperties(prefix = "jwt")` tells Spring:
> *"Go find all properties that start with `jwt.` in the config file and map them into this class."*

So if your `application.yml` looks like this:

```yaml
jwt:
  secret: my-super-secret-key
  access-token-expiry-ms: 900000
  refresh-token-expiry-ms: 604800000
```

Spring reads those values and automatically calls the setters on this class to fill in the fields. You don't wire anything manually — Spring handles it.

---

### 2. The three fields

| Field | What it holds |
|---|---|
| `secret` | The key used to sign and verify JWT tokens |
| `accessTokenExpiryMs` | How long an access token stays valid (in milliseconds) |
| `refreshTokenExpiryMs` | How long a refresh token stays valid (in milliseconds) |

**`secret`**
This is the most sensitive piece. It is used to cryptographically sign every JWT token your app issues. If someone gets hold of this key, they can forge tokens and impersonate any user. This is why it lives in config — never hardcoded — and in production it would come from an environment variable or a secrets manager.

**`accessTokenExpiryMs`**
Access tokens are short-lived by design. A typical value might be 15 minutes (`900000` ms). Once expired, the user's request is rejected and they need a fresh token.

**`refreshTokenExpiryMs`**
Refresh tokens live longer — days or weeks. They exist specifically to get new access tokens without making the user log in again. A typical value might be 7 days (`604800000` ms).

---

### 3. The getters and setters

Nothing fancy here. These are standard Java getters and setters.

Spring uses the **setters** at startup to inject the values from the config file. The rest of the app uses the **getters** to read those values — for example, when `JwtService` needs the secret to sign a token, or when `JwtAuthFilter` needs the expiry to check if a token has expired.

---

## 🔥 One-Line Summary

> This class reads your JWT settings out of the config file once at startup and makes them available to the rest of the app.

---

## 💡 Deep Dive: Why not just hardcode these values?

You could write this instead:

```java
private String secret = "my-super-secret-key";
private long accessTokenExpiryMs = 900000;
```

And it would work. But here is why that is a bad idea.

---

### ❌ Problem 1: Secrets in source code

If your secret is hardcoded, it lives in your Git repository. Anyone who can read the repo can read the secret — including old commits even after you change it.

In production, secrets should come from the environment — not from code.

---

### ❌ Problem 2: Changing values means redeploying

If expiry times are hardcoded, changing them means editing code, committing, and redeploying. With config-driven values, you change one line in a config file or environment variable and restart.

---

### ✅ The config-driven way

| Environment | Where the values come from |
|---|---|
| Local dev | `application.yml` |
| Staging / Production | Environment variables or a secrets manager |

The code never changes. Only the config does.

---

### The analogy

| Approach | Analogy |
|---|---|
| Hardcoded secret | Writing your house key's shape directly into the blueprint — everyone who reads the blueprint can copy your key |
| Config-driven secret | The key lives in a safe. Different people get access to different safes depending on the environment. |

---

### Final one-liner

> Never put secrets or environment-specific values in code. Put them in config — and use a class like `JwtConfig` to bring them in cleanly.