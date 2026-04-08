# ConflictException — Code Prose

`com.miniagoda.common.exception.ConflictException`

---

## Overview

This class gives the application a named, specific exception for the case where an operation cannot proceed because it would violate a uniqueness constraint or clash with existing state.

Like `NotFoundException`, it extends `RuntimeException` and carries no logic of its own. It is unchecked, passes its message straight to the parent, and relies entirely on a global exception handler to catch it and translate it into an appropriate HTTP response — in this case, a `409 Conflict`.

The scenarios it covers are those where the request itself is well-formed, but the data behind it already exists: a user registering with an email address that is taken, a resource being created under a name that is already in use. The operation is not invalid in structure; it is incompatible with the current state of the system. That is a meaningfully different failure from a missing resource or a validation error, and `ConflictException` exists to name that difference precisely so the handler can respond to it accordingly.

# ConflictException — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **"already taken" alarm**.

Where `NotFoundException` fires when something doesn't exist, `ConflictException` fires when something already exists and you're trying to create it again — or when two things clash in a way that makes the operation impossible to complete.

| Concept | What it is |
|---|---|
| `ConflictException` | A named exception that means "this operation conflicts with the current state of things" |
| `extends RuntimeException` | Makes it unchecked — bubbles up automatically to the global handler |

---

## 🧩 Step-by-Step in Plain Terms

### 1. `extends RuntimeException` — same pattern as `NotFoundException`

Same reasoning as before. Unchecked, so it bubbles up without forcing every method in the call chain to declare it. The global exception handler catches it and maps it to a `409 Conflict` response automatically.

---

### 2. The constructor

```java
public ConflictException(String message) {
    super(message);
}
```

Takes a message, passes it to `RuntimeException`. The caller provides the context:

```java
throw new ConflictException("Email address is already registered");
throw new ConflictException("Username 'shawon' is already taken");
throw new ConflictException("A booking already exists for these dates");
```

The class stays generic. The specifics come from wherever it's thrown.

---

## 🔥 One-Line Summary

> A named, unchecked exception that gets thrown whenever an operation can't proceed because of a clash with something that already exists.

---

## 💡 Deep Dive: When does a conflict actually happen?

A conflict is not the same as a validation failure. Validation fails before you even look at the database — the input itself is wrong. A conflict happens after you look at the database and find something already there that blocks the operation.

---

### The key distinction

| Scenario | Exception |
|---|---|
| Email field is blank | `ValidationException` — bad input, never check the DB |
| Email field is valid but already registered | `ConflictException` — input is fine, DB says no |
| Hotel ID in the URL doesn't exist | `NotFoundException` — looked it up, not there |
| Trying to create a hotel that already exists | `ConflictException` — looked it up, already there |

The sequence matters. You validate first. Then you query. Then you throw a conflict if the state of the data prevents the operation.

---

### The typical flow in a service method

```java
public void registerUser(RegisterRequest request) {

    // 1. Validation happens before this point (e.g. @Valid on the controller)

    // 2. Check current state of the database
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new ConflictException("Email address is already registered");
    }

    // 3. Safe to proceed
    userRepository.save(new User(request));
}
```

The service doesn't return a status code or build an error response. It just throws. The global handler takes care of the rest — catches `ConflictException`, maps it to `409`, wraps it in an `ErrorResponse`, and sends it back.

---

### Why `409` and not `400`?

`400 Bad Request` means the input itself was wrong — malformed, missing, invalid.

`409 Conflict` means the input was perfectly fine but the current state of the server made the operation impossible. The email address `shawon@gmail.com` is a valid email. The problem isn't the format — it's that someone already owns it.

That distinction matters for the frontend. A `400` means "fix your input." A `409` means "this specific value is already taken — try a different one."

---

### How the full exception family maps to HTTP

| Exception | HTTP Status | Meaning |
|---|---|---|
| `NotFoundException` | `404` | Resource doesn't exist |
| `ConflictException` | `409` | Resource already exists or states clash |
| `UnauthorizedException` | `401` | Not logged in |
| `ForbiddenException` | `403` | Logged in but not allowed |
| `ValidationException` | `400` | Input failed business rules |

---

### The analogy

| Thing | Analogy |
|---|---|
| `NotFoundException` | You try to check in to room 404 — the room doesn't exist |
| `ConflictException` | You try to book room 12 — it's already occupied |
| `ValidationException` | You hand in a booking form with no name written on it — the form itself is wrong |
| Global exception handler | The front desk — it sees the problem type and handles it the right way every time |

---

### Final one-liner

> `ConflictException` is for when the input is valid but the world says no — something is already there, already taken, or already in a state that blocks what you're trying to do.