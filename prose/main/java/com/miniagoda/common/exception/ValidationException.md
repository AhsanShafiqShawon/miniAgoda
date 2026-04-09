# ValidationException — Code Prose

`com.miniagoda.common.exception.ValidationException`

---

## Overview

This class gives the application a named, specific exception for the case where a request is structurally sound but its contents fail to meet the rules the application expects.

It extends `RuntimeException`, is unchecked, and relies on a global exception handler to catch it and produce a `400 Bad Request` response. Like `UnauthorizedException` and `ForbiddenException`, it pairs a no-argument constructor carrying a sensible default with an overloaded constructor for cases that need a more specific message.

The territory it covers is distinct from the other exceptions in the package. `NotFoundException` means something was not there. `ConflictException` means something was already there. `UnauthorizedException` and `ForbiddenException` are about identity and permission. `ValidationException` is about the shape and content of the data itself — a required field that was omitted, a value that falls outside an acceptable range, a format that does not match what the application knows how to handle. The request arrived; it just did not say what it needed to say.

The default message — `"The request contains invalid or missing fields."` — is intentionally broad. It acknowledges the failure without enumerating it, which is appropriate when the exception is thrown in a general context. When the handler needs to surface field-level detail to the caller, that is the job of `ErrorResponse`'s `withFieldErrors` factory, not of the exception message itself. The exception names the category of failure; the response shapes how it is communicated.

# ValidationException — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **"you filled the form out wrong" alarm**.

It fires before anything else happens — before a database lookup, before a conflict check, before any business logic runs. The input itself is the problem. Something is missing, malformed, or breaks a rule that has nothing to do with the current state of the data.

| Concept | What it is |
|---|---|
| `ValidationException` | A named exception that means "the input itself is wrong" |
| `extends RuntimeException` | Unchecked — bubbles up to the global handler automatically |

---

## 🧩 Step-by-Step in Plain Terms

### 1. Two constructors — same pattern as the rest of the family

**The no-argument constructor:**
```java
public ValidationException() {
    super("The request contains invalid or missing fields.");
}
```

The default. A general-purpose message for when the validation failure is already captured elsewhere — for example, in a `fieldErrors` map inside `ErrorResponse`. The message here acts as a summary heading, not the full explanation.

**The message constructor:**
```java
public ValidationException(String message) {
    super(message);
}
```

For cases where one specific rule failed and the message tells the whole story on its own — `"Check-out date must be after check-in date"` or `"Guest count cannot exceed room capacity"`. No field map needed — the message is the error.

---

### 2. The default message

```
"The request contains invalid or missing fields."
```

Deliberately broad. This message is typically paired with a `fieldErrors` map in `ErrorResponse` — the summary says "something in the fields is wrong" and the map says exactly which fields and why. Together they give the frontend everything it needs to highlight the right inputs.

---

## 🔥 One-Line Summary

> Thrown when the input itself is wrong — bad format, missing value, or a rule broken before the database is ever touched — and maps to a `400` in the global handler.

---

## 💡 Deep Dive: Two kinds of validation

Not all validation is the same. There are two distinct layers where input gets checked, and it's worth knowing which does what.

---

### Layer 1 — Annotation-based validation (`@Valid`)

Spring has a built-in validation system. You annotate your request DTO fields:

```java
public record RegisterRequest(
    @NotBlank String name,
    @Email String email,
    @Size(min = 8) String password
) {}
```

And your controller declares:

```java
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
```

If any field fails, Spring throws a `MethodArgumentNotValidException` before your code even runs. Your global exception handler catches that, extracts the field errors, and wraps them in an `ErrorResponse` using `withFieldErrors(...)`.

`ValidationException` is not involved here. Spring handles it entirely.

---

### Layer 2 — Business rule validation (manual, in the service)

Some rules can't be expressed as annotations. They require logic.

```java
public void createBooking(BookingRequest request) {
    if (request.checkOutDate().isBefore(request.checkInDate())) {
        throw new ValidationException("Check-out date must be after check-in date");
    }

    if (request.guests() > hotel.getMaxCapacity()) {
        throw new ValidationException("Guest count exceeds room capacity");
    }

    // Proceed
}
```

These are business rules — context-dependent, logic-driven, impossible to capture with a simple annotation. This is where `ValidationException` gets thrown manually.

---

### The difference in plain terms

| Layer | Who throws it | When | Example |
|---|---|---|---|
| Annotation (`@Valid`) | Spring automatically | Before the method runs | Email field is blank |
| Business rule (manual) | Your service code | Inside the method | Check-out is before check-in |

Both produce a `400`. The path to getting there is just different.

---

### Where `ValidationException` sits in the sequence

Every service method follows roughly the same order:

```
1. Validation       ← ValidationException lives here
2. Existence check  ← NotFoundException lives here
3. Conflict check   ← ConflictException lives here
4. Permission check ← ForbiddenException lives here
5. Do the work
```

Validation always runs first. There's no point querying the database if the input is already wrong. Fail fast, fail early, don't waste the round trip.

---

### How the full exception family maps to HTTP

| Exception | HTTP Status | Meaning |
|---|---|---|
| `ValidationException` | `400` | Input failed business rules |
| `UnauthorizedException` | `401` | Identity not proven |
| `ForbiddenException` | `403` | Identity known, access denied |
| `NotFoundException` | `404` | Resource doesn't exist |
| `ConflictException` | `409` | Resource already exists or states clash |

---

### The analogy

| Thing | Analogy |
|---|---|
| Annotation validation | The form rejects your submission instantly — the email field is blank, it won't even let you click submit |
| `ValidationException` (business rule) | The form submits fine, but the booking desk reads it and says "you can't check out before you check in — please fix this" |
| `NotFoundException` | The desk processes your form but finds no room with that number |
| `ConflictException` | The desk finds the room but it's already booked for those dates |

---

### Final one-liner

> Validation is always the first check — catch bad input before it wastes a database call, and throw `ValidationException` when a business rule fails that no annotation could have caught.