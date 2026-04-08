# NotFoundException — Code Prose

`com.miniagoda.common.exception.NotFoundException`

---

## Overview

This class gives the application a named, specific exception for the case where a requested resource does not exist.

It extends `RuntimeException`, which means it is unchecked — callers are not forced to declare or catch it. In a Spring application this is the conventional choice for domain exceptions: rather than cluttering every service method signature with `throws` clauses, the exception is allowed to propagate up the call stack until something — typically a global exception handler — catches it, maps it to an `ErrorResponse`, and returns an appropriate HTTP response to the caller.

The class itself carries no logic beyond passing the message to its parent. Its value is not in what it does but in what it is. A service that throws `NotFoundException` is communicating something precise: not that an unexpected error occurred, but that a specific lookup came back empty. That distinction matters when the exception handler needs to decide which HTTP status code and error label to attach to the response.

# NotFoundException — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as a **custom alarm** your app pulls when something that should exist doesn't.

Java already has built-in exceptions. But generic exceptions like `RuntimeException` don't tell you anything meaningful — they just say "something went wrong." `NotFoundException` says something specific: *a resource was looked up and wasn't there.* That specificity is what makes error handling clean and readable across the whole codebase.

| Concept | What it is |
|---|---|
| `NotFoundException` | A named exception that means "the thing you asked for doesn't exist" |
| `extends RuntimeException` | Makes it unchecked — callers don't have to declare or catch it |

---

## 🧩 Step-by-Step in Plain Terms

### 1. `extends RuntimeException` — unchecked by design

In Java there are two kinds of exceptions:

**Checked exceptions** — the compiler forces you to either catch them or declare them with `throws`. Every method in the call chain has to acknowledge them.

**Unchecked exceptions** — extend `RuntimeException`. The compiler doesn't care. They bubble up automatically until something catches them — or they reach a global handler.

`NotFoundException` extends `RuntimeException`, making it unchecked. This is intentional. You don't want every service method to be cluttered with `throws NotFoundException` declarations just because it does a database lookup. The exception bubbles up cleanly to wherever it's meant to be caught — typically a global exception handler.

---

### 2. The constructor

```java
public NotFoundException(String message) {
    super(message);
}
```

The constructor takes a message and passes it straight up to `RuntimeException`. That message is what gets logged and what ends up in the error response the API sends back.

The caller decides what the message says:

```java
throw new NotFoundException("Hotel with ID 42 not found");
throw new NotFoundException("User not found");
throw new NotFoundException("Booking with ID 7 does not exist");
```

The class itself stays generic and reusable. The specific context comes from wherever it's thrown.

---

## 🔥 One-Line Summary

> A named, unchecked exception that gets thrown whenever a resource lookup comes up empty — clean to throw, clean to catch, and readable at a glance.

---

## 💡 Deep Dive: Why not just throw `RuntimeException` directly?

You could do this anywhere in the codebase:

```java
throw new RuntimeException("Hotel not found");
```

And it would work. But here is why that is a problem.

---

### ❌ Problem 1: You can't catch it selectively

If everything throws `RuntimeException`, your global exception handler has no way to distinguish between a "not found" error and a "something crashed" error. They're the same type.

With a named exception, the handler can do this:

```java
@ExceptionHandler(NotFoundException.class)
public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
    // respond with 404
}

@ExceptionHandler(RuntimeException.class)
public ResponseEntity<ErrorResponse> handleGeneric(RuntimeException ex) {
    // respond with 500
}
```

`NotFoundException` always maps to a `404`. Everything else falls through to the generic handler. Clean, automatic, no manual status code decisions scattered across your services.

---

### ❌ Problem 2: The code becomes harder to read

```java
throw new RuntimeException("Hotel not found");
```

vs.

```java
throw new NotFoundException("Hotel with ID 42 not found");
```

The second one tells you exactly what happened without reading the message. When you're scanning a service method at speed, named exceptions communicate intent instantly.

---

### How this fits into the bigger picture

`NotFoundException` is one piece of a family. A mature exception hierarchy for this app will likely look something like this:

| Exception | HTTP Status | When it gets thrown |
|---|---|---|
| `NotFoundException` | `404` | Resource doesn't exist |
| `UnauthorizedException` | `401` | Not logged in |
| `ForbiddenException` | `403` | Logged in but not allowed |
| `ConflictException` | `409` | Resource already exists |
| `ValidationException` | `400` | Input failed business rules |

Each one maps to a specific HTTP status in the global exception handler. The services just throw the right named exception and let the handler deal with the response.

---

### The analogy

| Thing | Analogy |
|---|---|
| `RuntimeException` | A fire alarm with no label — something is wrong, but you don't know where or why |
| `NotFoundException` | A specific alarm labeled "Room 404" — you know exactly what triggered it and how to respond |
| Global exception handler | The control panel that sees which alarm went off and sends the right crew |

---

### Final one-liner

> Named exceptions cost almost nothing to write and give you precise, automatic control over what HTTP status gets returned and how each failure type gets handled.