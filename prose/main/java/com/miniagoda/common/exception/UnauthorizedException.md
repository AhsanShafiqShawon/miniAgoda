# UnauthorizedException — Code Prose

`com.miniagoda.common.exception.UnauthorizedException`

---

## Overview

This class gives the application a named, specific exception for the case where a request reaches a protected resource without valid credentials.

It extends `RuntimeException` for the same reasons as its siblings: it is unchecked, it propagates freely up the call stack, and it relies on a global exception handler to catch it and produce a `401 Unauthorized` response. The class carries no logic beyond its constructors.

What distinguishes it from `NotFoundException` and `ConflictException` is that it ships with a default message. Most of the time, an authentication failure does not need a custom explanation — the situation is the same regardless of which endpoint triggered it, and a consistent, neutral message is preferable to one that varies by context or risks revealing which resources exist. The no-argument constructor exists precisely for that common case: the code that throws it does not have to think about what to say.

The second constructor, which accepts a message, is there for the cases where the default is not specific enough — a token that has expired, a session that was revoked, a context where the distinction matters. The two constructors together make the common path effortless and the specific path possible.

# UnauthorizedException — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **"who are you?" alarm**.

It fires when a request reaches a protected part of the app without proving who it is. Not the wrong person — just no proof at all. No token, expired token, invalid token. The server can't even begin to check permissions because it doesn't know who's asking.

| Concept | What it is |
|---|---|
| `UnauthorizedException` | A named exception that means "you haven't proven your identity" |
| `extends RuntimeException` | Unchecked — bubbles up to the global handler automatically |

---

## 🧩 Step-by-Step in Plain Terms

### 1. Two constructors — one detail worth noticing

Most exception classes in this codebase have one constructor. This one has two.

**The no-argument constructor:**
```java
public UnauthorizedException() {
    super("Authentication is required to access this resource.");
}
```

This is the default. It has a pre-written message baked in. The caller doesn't need to think of anything — just throw it and the right message goes out automatically.

**The message constructor:**
```java
public UnauthorizedException(String message) {
    super(message);
}
```

For cases where the default message isn't specific enough. Maybe the token has expired and you want to say so explicitly. Maybe it was malformed. The caller can override the message when the extra context is worth surfacing.

---

### 2. The default message

```
"Authentication is required to access this resource."
```

This is a deliberate choice — vague enough to be safe, clear enough to be useful.

You don't want to say *why* authentication failed in too much detail. Telling a caller "your token signature was invalid" or "no token was found in the header" gives information that could help an attacker understand what to fix. The default message tells the caller what they need to do — authenticate — without revealing anything about the internals.

---

## 🔥 One-Line Summary

> Thrown when a request can't prove who it is — no token, bad token, or expired token — and maps cleanly to a `401` in the global handler.

---

## 💡 Deep Dive: `401 Unauthorized` vs `403 Forbidden`

These two are the most commonly confused status codes in API design. They sound similar. They are not the same.

---

### The core difference

| Status | Meaning | Analogy |
|---|---|---|
| `401 Unauthorized` | We don't know who you are | Showing up to a hotel with no ID |
| `403 Forbidden` | We know who you are — you're just not allowed | Showing up with a valid ID but you're not on the guest list |

`401` is an identity problem. `403` is a permissions problem.

---

### In practice

```java
// No token in the request header — we don't know who this is
throw new UnauthorizedException();

// Token is valid, user is identified — but they're a GUEST trying to access /api/admin
throw new ForbiddenException("You do not have permission to access this resource.");
```

The sequence in `JwtAuthFilter` will look something like this:

1. Check for token in the `Authorization` header → no token → `UnauthorizedException`
2. Validate the token signature → invalid → `UnauthorizedException`
3. Check token expiry → expired → `UnauthorizedException`
4. Load the user → not found → `UnauthorizedException`
5. Check role against the route → wrong role → `ForbiddenException`

Everything up to and including step 4 is an identity failure — `401`. Step 5 is a permissions failure — `403`.

---

### Why the distinction matters for the frontend

A `401` tells the frontend:
> *"Send the user to the login page. They need to authenticate."*

A `403` tells the frontend:
> *"The user is logged in. Don't redirect to login. Show them an 'access denied' screen instead."*

If you collapse both into `401` or both into `403`, the frontend can't make that distinction. Users get sent to login screens when they're already logged in, or stay on broken pages when they should be prompted to re-authenticate.

---

### How the full exception family maps to HTTP

| Exception | HTTP Status | Meaning |
|---|---|---|
| `NotFoundException` | `404` | Resource doesn't exist |
| `ConflictException` | `409` | Resource already exists or states clash |
| `UnauthorizedException` | `401` | Identity not proven |
| `ForbiddenException` | `403` | Identity known, access denied |
| `ValidationException` | `400` | Input failed business rules |

---

### The analogy

| Thing | Analogy |
|---|---|
| `UnauthorizedException` | Arriving at a VIP lounge with no ID — the bouncer stops you before asking anything else |
| `ForbiddenException` | Arriving with a valid ID that says "General Admission" — the bouncer knows exactly who you are and still says no |
| Default message | The bouncer saying "you need to show ID" — helpful, but not telling you where the ID checker is or what they're looking for |

---

### Final one-liner

> `401` means the server doesn't know who you are. `403` means it knows exactly who you are — and that's the problem.