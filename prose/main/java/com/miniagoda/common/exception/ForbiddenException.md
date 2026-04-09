# ForbiddenException — Code Prose

`com.miniagoda.common.exception.ForbiddenException`

---

## Overview

This class gives the application a named, specific exception for the case where a request comes from a caller who is authenticated but not permitted to perform the operation they are attempting.

It extends `RuntimeException`, is unchecked, and defers to a global exception handler to translate it into a `403 Forbidden` response. In structure it is identical to `UnauthorizedException`: a no-argument constructor with a default message, and a second constructor for cases that need something more specific.

The distinction between the two exceptions is worth being precise about. `UnauthorizedException` means the caller has not proven who they are — credentials are missing or invalid. `ForbiddenException` means the caller is known, but their identity is not enough. They are in the building; they just do not have access to this room. Mapping these two situations to separate exception types ensures the handler can return the semantically correct status code for each, which matters both for correctness and for any client that needs to decide whether to prompt for login or simply surface a permissions error.

The default message — `"You do not have permission to perform this action."` — is deliberately generic. A message that is too specific about what was attempted or why it was denied can inadvertently confirm the existence of resources or operations a caller should not even know about. The overloaded constructor exists for internal contexts where that caution does not apply and a more informative message is appropriate.

# ForbiddenException — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **"we know you, but no" alarm**.

Where `UnauthorizedException` fires when the server doesn't know who you are, `ForbiddenException` fires when it knows exactly who you are — and that's precisely the problem. Your identity has been confirmed. Your role just doesn't have access to what you're asking for.

| Concept | What it is |
|---|---|
| `ForbiddenException` | A named exception that means "identity confirmed, access denied" |
| `extends RuntimeException` | Unchecked — bubbles up to the global handler automatically |

---

## 🧩 Step-by-Step in Plain Terms

### 1. Two constructors — same pattern as `UnauthorizedException`

**The no-argument constructor:**
```java
public ForbiddenException() {
    super("You do not have permission to perform this action.");
}
```

The default. Pre-written message, ready to throw. Most cases don't need anything more specific than this — the user either has permission or they don't.

**The message constructor:**
```java
public ForbiddenException(String message) {
    super(message);
}
```

For cases where the default isn't enough. If you want to say "only hosts can list properties" or "admins only", the caller can pass that in. Use sparingly — giving too much detail about permission boundaries can be a security concern in its own right.

---

### 2. The default message

```
"You do not have permission to perform this action."
```

Notice the wording — "perform this action", not "access this resource." That's a subtle but meaningful difference.

`UnauthorizedException` says "authentication is required to *access* this resource" — it's about identity and presence.

`ForbiddenException` says "you do not have permission to *perform this action*" — it's about role and capability. The user is there. They just can't do what they're trying to do.

---

## 🔥 One-Line Summary

> Thrown when the server knows who you are but your role doesn't allow the operation — maps to a `403` in the global handler.

---

## 💡 Deep Dive: Where does `ForbiddenException` actually get thrown?

Unlike `NotFoundException` or `ConflictException`, which are thrown inside service methods after a DB lookup, `ForbiddenException` typically fires in one of two places.

---

### Place 1 — The security filter (role-based route protection)

Once `JwtAuthFilter` is wired in, Spring Security will check the user's role against the route they're trying to access. A `GUEST` hitting `/api/host/**` or `/api/admin/**` will be blocked before the request even reaches a controller.

In this case Spring Security handles the `403` response directly — `ForbiddenException` may not even be thrown explicitly. The framework just rejects the request.

---

### Place 2 — Service-level ownership checks

This is where `ForbiddenException` gets thrown explicitly. Some operations aren't just role-gated — they're ownership-gated. A host can manage hotels, but only *their own* hotels.

```java
public void updateHotel(String hotelId, UpdateHotelRequest request, String requestingUserId) {
    Hotel hotel = hotelRepository.findById(hotelId)
            .orElseThrow(() -> new NotFoundException("Hotel not found"));

    if (!hotel.getOwnerId().equals(requestingUserId)) {
        throw new ForbiddenException();
    }

    // Safe to proceed
}
```

The user is authenticated. They have the `HOST` role. But this specific hotel belongs to someone else. Role alone isn't enough — ownership matters too. That's when `ForbiddenException` gets thrown manually inside the service.

---

### The decision tree for access failures

```
Request comes in
        ↓
Is there a valid token?
   No  → UnauthorizedException (401)
        ↓
Does the user's role allow this route?
   No  → ForbiddenException (403) — handled by Spring Security
        ↓
Does the user own this specific resource?
   No  → ForbiddenException (403) — thrown manually in the service
        ↓
Proceed
```

---

### Why ownership checks can't live in the filter

The filter only knows the route and the role. It doesn't know which specific resource is being accessed or who owns it. That context only exists inside the service, after the database has been queried. So the filter handles role gates, and the service handles ownership gates — both throw `ForbiddenException`, just from different layers.

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
| `UnauthorizedException` | Arriving at the hotel with no ID — the front desk can't help you |
| `ForbiddenException` (route) | You have a guest key card but you're trying to enter the staff corridor — the door reader rejects it immediately |
| `ForbiddenException` (ownership) | You have a valid staff key card, but you're trying to enter *another host's* private office — you're staff, just not the right staff |
| Default message | The door reader flashing red — no explanation needed, the answer is just no |

---

### Final one-liner

> `ForbiddenException` fires in two places — at the route level when the role is wrong, and at the service level when the role is right but the resource belongs to someone else.