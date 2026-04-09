# GlobalExceptionHandler — Code Prose

`com.miniagoda.common.exception.GlobalExceptionHandler`

---

## Overview

This class is where every unhandled exception in the application lands, and where it is converted into a structured HTTP response.

It is annotated with `@RestControllerAdvice`, which tells Spring to apply it across all controllers in the application. When an exception propagates out of a controller without being caught locally, Spring intercepts it here before anything reaches the client. Each handler method is annotated with `@ExceptionHandler`, declaring which exception type it is responsible for. Spring routes the exception to the matching method, which builds an `ErrorResponse` and wraps it in a `ResponseEntity` with the appropriate HTTP status.

Every handler receives the raw `HttpServletRequest` alongside the exception. This is how the `path` field of `ErrorResponse` is populated — `request.getRequestURI()` provides the exact URI that triggered the failure, making every error response self-locating without any additional instrumentation.

---

## `handleNotFound`

Catches `NotFoundException` and responds with `404 Not Found`.

The exception's own message is passed through directly to `ErrorResponse.of`. Because `NotFoundException` is thrown deliberately by service code that knows what it was looking for, the message is already specific and appropriate to surface.

---

## `handleConflict`

Catches `ConflictException` and responds with `409 Conflict`.

The same pattern applies: the exception carries its own message, and the handler's only job is to pair it with the correct status code and wrap it in the standard response shape.

---

## `handleUnauthorized`

Catches `UnauthorizedException` and responds with `401 Unauthorized`.

By the time an `UnauthorizedException` reaches this handler, it carries either its default message or one supplied at the throw site. Either way, the handler passes it through unchanged. The distinction between this handler and `handleForbidden` — `401` versus `403` — is what gives the two exception classes their separate existence.

---

## `handleForbidden`

Catches `ForbiddenException` and responds with `403 Forbidden`.

Structurally identical to `handleUnauthorized`. The work of distinguishing a missing-identity failure from a missing-permission failure was done when the exception was thrown; this handler simply honours that distinction by mapping it to the correct status code.

---

## `handleValidation`

Catches `ValidationException` and responds with `400 Bad Request`.

This handler covers manually thrown validation failures — cases where service code inspects its inputs and decides they are unacceptable, but the failure does not involve individual fields. The message comes from the exception itself, and `ErrorResponse.of` is used rather than `withFieldErrors` because there is no field-level breakdown to attach.

---

## `handleMethodArgumentNotValid`

Catches `MethodArgumentNotValidException` and responds with `400 Bad Request`, with field-level detail.

This handler covers a different kind of validation failure: the kind Spring itself detects when a request body annotated with `@Valid` fails its constraints before the controller method is even called. Spring collects all the binding errors into a `BindingResult`, and this handler extracts them into a `Map<String, String>` — each field name paired with its corresponding error message.

The stream that builds the map includes a merge function as a third argument to `Collectors.toMap`. This handles the edge case where the same field produces more than one error: rather than throwing an exception on a duplicate key, the collector retains the first message it encountered and discards the rest. Only one message per field reaches the client, keeping the response clean.

The resulting map is passed to `ErrorResponse.withFieldErrors`, which is the only place in the handler that uses this factory — it is exactly the use case `withFieldErrors` was designed for.

---

## `handleGeneric`

Catches `Exception` — the root of the Java exception hierarchy — and responds with `500 Internal Server Error`.

This is the safety net. Any exception that does not match a more specific handler above will fall through to this one. The message it returns to the client is deliberately fixed: `"An unexpected error occurred."` The actual exception message is not passed through, because at this level the failure is genuinely unexpected and its message may contain internal implementation details that should not leave the server. The error is visible in logs; the client receives only enough to know that something went wrong on the server's side.

Its presence also ensures the application never responds to an unhandled exception with an empty body or a raw Spring error page. Every failure, no matter how unanticipated, resolves to a well-formed `ErrorResponse`.

# GlobalExceptionHandler — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **central control panel** that receives every alarm and decides what to send back.

Every custom exception in the codebase — `NotFoundException`, `ConflictException`, `UnauthorizedException`, `ForbiddenException`, `ValidationException` — is useless on its own without something to catch it. This class is that something. It intercepts every exception before it reaches the user, translates it into a proper HTTP response, and sends it back in the standard `ErrorResponse` shape.

| Concept | What it is |
|---|---|
| `GlobalExceptionHandler` | The single place that catches all exceptions and turns them into API responses |
| `@RestControllerAdvice` | Tells Spring to apply this handler to every controller in the app |
| `@ExceptionHandler` | Marks each method as the handler for a specific exception type |

---

## 🧩 Step-by-Step in Plain Terms

### 1. `@RestControllerAdvice` — one handler for the whole app

Without this, you'd have to wrap every controller method in a `try-catch` and build an error response manually every time. That's dozens of places all doing the same thing, all potentially inconsistent.

`@RestControllerAdvice` tells Spring:

> *"Whenever any exception escapes from any controller in this application, bring it here first."*

One class. Every exception. Handled consistently.

---

### 2. `handleNotFound` — catches `NotFoundException`

```java
@ExceptionHandler(NotFoundException.class)
public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
```

When anything in the app throws a `NotFoundException`, Spring routes it here. The method reads the message off the exception, reads the URL off the request, and builds a `404 Not Found` response using `ErrorResponse.of(...)`.

The caller — the service, the filter, wherever — just throws. It never touches status codes or response bodies. That work happens here.

---

### 3. `handleConflict` — catches `ConflictException`

Same pattern. `ConflictException` bubbles up, lands here, becomes a `409 Conflict` response. The message from the exception — "Email address is already registered", for example — goes straight into the response body.

---

### 4. `handleUnauthorized` — catches `UnauthorizedException`

`UnauthorizedException` lands here and becomes a `401 Unauthorized` response. The default message — "Authentication is required to access this resource." — travels with it unless the caller overrode it.

---

### 5. `handleForbidden` — catches `ForbiddenException`

`ForbiddenException` lands here and becomes a `403 Forbidden` response. The distinction from `401` is preserved — the frontend receives a different status code and can react differently.

---

### 6. `handleValidation` — catches `ValidationException`

Manual business rule failures land here and become `400 Bad Request` responses. The message from the exception — "Check-out date must be after check-in date", for example — goes into the response as-is. No field map, just the message.

---

### 7. `handleMethodArgumentNotValid` — catches Spring's own validation failures

This one is different. It doesn't catch a custom exception — it catches `MethodArgumentNotValidException`, which Spring throws automatically when `@Valid` fails on a request body.

```java
Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
                error -> error.getField(),
                error -> error.getDefaultMessage(),
                (existing, duplicate) -> existing
        ));
```

Spring gives back a list of field errors. This method walks that list and collapses it into a map — field name on the left, error message on the right. If the same field has multiple failures, the first one wins (that's what the merge function `(existing, duplicate) -> existing` does).

That map then goes into `ErrorResponse.withFieldErrors(...)` — the only place in the whole handler that uses the field errors variant. The result is a `400` response with a structured breakdown of exactly which fields failed and why.

---

### 8. `handleGeneric` — catches everything else

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
```

This is the safety net. Any exception that doesn't match a specific handler above — a null pointer, a database timeout, an unexpected runtime crash — falls through to here and becomes a `500 Internal Server Error`.

Crucially, the message is hardcoded:

```
"An unexpected error occurred."
```

The actual exception message is deliberately not forwarded to the client. Internal error details — stack traces, database error messages, class names — should never reach the outside world. They get logged internally, but the user sees only a safe, generic message.

---

## 🔥 One-Line Summary

> Every exception thrown anywhere in the app lands here, gets matched to the right handler, and leaves as a consistent `ErrorResponse` — no status codes scattered across services, no raw error messages leaking to clients.

---

## 💡 Deep Dive: Why centralise all of this?

Without a global handler, error handling lives everywhere. Every service, every controller, every filter has to decide what status code to use and what the response body looks like. That leads to:

- Inconsistent response shapes — some endpoints return plain strings on error, some return JSON objects, some return nothing
- Duplicate logic — the same `try-catch` pattern copy-pasted across dozens of methods
- Leaking internals — a developer forgets to sanitise an error message and a stack trace reaches the client

With `GlobalExceptionHandler`, the contract is simple:

> *Services throw named exceptions. The handler catches them and sends the right response. Always.*

---

### The full exception-to-response map

| Exception thrown | Handler method | HTTP status | `ErrorResponse` variant |
|---|---|---|---|
| `NotFoundException` | `handleNotFound` | `404` | `of(...)` |
| `ConflictException` | `handleConflict` | `409` | `of(...)` |
| `UnauthorizedException` | `handleUnauthorized` | `401` | `of(...)` |
| `ForbiddenException` | `handleForbidden` | `403` | `of(...)` |
| `ValidationException` | `handleValidation` | `400` | `of(...)` |
| `MethodArgumentNotValidException` | `handleMethodArgumentNotValid` | `400` | `withFieldErrors(...)` |
| Anything else | `handleGeneric` | `500` | `of(...)` with safe message |

---

### The merge function in `handleMethodArgumentNotValid`

```java
(existing, duplicate) -> existing
```

This one line is easy to overlook. When Spring validates a request body, the same field can sometimes produce more than one error — `@NotBlank` and `@Size` both failing on the same field, for example. `Collectors.toMap` throws an exception if it encounters duplicate keys. The merge function tells it: if a field already has an error, keep the first one and discard the rest. One field, one message. Clean map, no crashes.

---

### Why the generic handler hides the real message

```java
body(ErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred.", request.getRequestURI()))
```

The actual `ex.getMessage()` is not used here — and that's intentional. A raw exception message might contain:

- Database table names or column names
- Internal class paths or method names
- Query strings or parameter values
- Credentials or tokens in edge cases

None of that should reach a client. The generic handler swallows the detail and sends back a safe string. The real exception should be logged separately so developers can diagnose it — but that's a logging concern, not a response concern.

---

### The analogy

| Thing | Analogy |
|---|---|
| Services throwing exceptions | Staff pulling specific alarms — "not found", "conflict", "unauthorized" |
| `GlobalExceptionHandler` | The central control room that receives every alarm and dispatches the right response |
| Named exception handlers | Trained crew for each alarm type — they know exactly what to do |
| Generic handler | The catch-all crew for alarms nobody planned for — they lock it down and say "something went wrong" without revealing why |
| Hiding the 500 message | The control room never broadcasts internal radio chatter over the public address system |

---

### Final one-liner

> `GlobalExceptionHandler` is the last line before a response leaves the server — it ensures every failure, planned or not, comes back as a clean, consistent, safe error response.