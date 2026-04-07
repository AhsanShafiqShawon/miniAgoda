# ErrorResponse — Code Prose

`com.miniagoda.common.response.ErrorResponse`

---

## Overview

This record defines the consistent shape the application sends back whenever something goes wrong.

Where `ApiResponse<T>` is the envelope for success, `ErrorResponse` is its counterpart for failure. Rather than letting exceptions bubble up as raw stack traces, or letting each error handler invent its own structure, every error the API surfaces — a missing resource, a validation failure, an unauthorised request — arrives at the caller in the same predictable form: a status code, a short error label, a human-readable message, the path that triggered the problem, the moment it occurred, and optionally a breakdown of which fields were at fault.

Like `ApiResponse`, it is a `record`. Error payloads have no reason to mutate once constructed, and the record syntax keeps the class concise without sacrificing any of the information a client needs to handle the error programmatically.

---

## Fields

**`status`** carries the HTTP status code as an integer — `400`, `404`, `500`, and so on. Including it in the body means the client does not have to rely solely on the HTTP layer to understand the nature of the failure; the body is self-describing.

**`error`** is a short, machine-friendly label for the error category — something like `"Bad Request"` or `"Not Found"`. It gives the caller a stable string to branch on without parsing the longer message.

**`message`** is the human-readable explanation. This is what a frontend might surface to the user, or what a developer reads when debugging. It should be specific enough to be useful but not so detailed that it leaks internal implementation.

**`path`** records the request URI that produced the error. In a system with many endpoints, this makes logs and error reports immediately actionable — there is no need to cross-reference request logs to find out where the failure came from.

**`timestamp`** captures the exact moment the error was constructed, as an `Instant`. Using `Instant` rather than a formatted string keeps the value timezone-neutral and lets clients format or compare it however they need.

**`fieldErrors`** is a map from field name to error message, and it is the only nullable field in the record. Most errors do not involve individual fields, so it is `null` by default and only populated when validation fails. A `Map<String, String>` is a natural shape here: each key is the name of the offending field, and each value is the reason it was rejected.

---

## `of(...)`

This factory method handles the common case: an error that is not caused by invalid input fields.

It fills all the structural fields — status, error label, message, and path — and sets `timestamp` to `Instant.now()` automatically, so callers never have to think about when to capture the time. `fieldErrors` is set to `null`, signalling to the client that this is a general error rather than a validation failure.

---

## `withFieldErrors(...)`

This factory method handles validation failures specifically.

It takes the same four structural arguments as `of`, but adds a `Map<String, String>` of field-level errors. The timestamp is still captured automatically. The result gives the client everything it needs to display targeted feedback — not just "something went wrong with your request," but precisely which fields failed and why.

The two factory methods together draw a clean line between error categories: use `of` for anything systemic, and `withFieldErrors` when the problem is with the shape of the data the caller sent.

# ErrorResponse — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **standard accident report** your API fills out whenever something goes wrong.

`ApiResponse` handles the good path — operations that succeed. `ErrorResponse` handles the bad path — validation failures, missing resources, unauthorized access, server errors. Same idea: one consistent shape, every time, so the frontend always knows what to expect when things go wrong.

| Concept | What it is |
|---|---|
| `ErrorResponse` | A consistent wrapper for every error your API sends back |
| `of(...)` | For general errors — something went wrong, here's what |
| `withFieldErrors(...)` | For validation errors — specific fields failed, here's which ones |

---

## 🧩 Step-by-Step in Plain Terms

### 1. The six fields

**`int status`**
The HTTP status code. `404` means not found. `401` means unauthorized. `400` means bad request. `500` means something broke on the server. The frontend uses this to decide how to react.

**`String error`**
A short label for the error type — something like `"Not Found"` or `"Bad Request"`. This mirrors the standard HTTP status label. It's machine-readable and consistent.

**`String message`**
A human-readable explanation of what actually went wrong. This is what you'd show in a log or potentially surface in the UI — `"Hotel with ID 42 not found"` or `"Access token has expired"`.

**`String path`**
The URL that was called when the error happened — for example, `/api/hotels/42`. Useful for debugging. When you're reading logs and something blew up, you want to know exactly which endpoint triggered it.

**`Instant timestamp`**
The exact moment the error occurred. Always set to `Instant.now()` at the time the response is built. Useful for correlating errors across logs.

**`Map<String, String> fieldErrors`**
A map of field names to error messages — only used when a form or request body fails validation. For example:

```json
{
  "email": "must be a valid email address",
  "checkInDate": "must not be in the past"
}
```

For general errors this is `null`. It only appears when individual fields are the problem.

---

### 2. `of(int status, String error, String message, String path)` — the general error factory

```java
public static ErrorResponse of(int status, String error, String message, String path) {
    return new ErrorResponse(status, error, message, path, Instant.now(), null);
}
```

Used when something went wrong but it's not about a specific field. A resource wasn't found. The user isn't authenticated. The server threw an exception. The timestamp is captured automatically and `fieldErrors` is left as `null` — there are no individual fields to report on.

---

### 3. `withFieldErrors(...)` — the validation error factory

```java
public static ErrorResponse withFieldErrors(int status, String error, String message, String path, Map<String, String> fieldErrors) {
    return new ErrorResponse(status, error, message, path, Instant.now(), fieldErrors);
}
```

Used when a request body fails validation — the user submitted a form and one or more fields didn't pass the rules. Everything is the same as `of()`, but the `fieldErrors` map is populated with exactly which fields failed and why.

The frontend can walk this map and display inline errors next to the right fields instead of just showing a generic failure message.

---

## 🔥 One-Line Summary

> This class wraps every error response in the same shape — status, label, message, path, timestamp, and optionally a map of field-level failures — so nothing bad ever comes back looking different.

---

## 💡 Deep Dive: Why separate `of` and `withFieldErrors`?

Both methods produce an `ErrorResponse`. The difference is intent.

`of()` is for errors that aren't about specific fields:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Hotel with ID 42 not found",
  "path": "/api/hotels/42",
  "timestamp": "2025-01-15T10:23:45Z",
  "fieldErrors": null
}
```

`withFieldErrors()` is for validation failures where the frontend needs to know exactly what to highlight:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/bookings",
  "timestamp": "2025-01-15T10:24:01Z",
  "fieldErrors": {
    "checkInDate": "must not be in the past",
    "guests": "must be at least 1"
  }
}
```

Having two named factory methods makes the call site clear. When another developer reads the code and sees `withFieldErrors(...)`, they immediately know a form validation failure is being handled — no need to look at what's being passed in.

---

### How this pairs with `ApiResponse`

`ApiResponse` and `ErrorResponse` are two sides of the same coin:

| Scenario | Response used |
|---|---|
| Operation succeeded, data returned | `ApiResponse.ok(data)` |
| Operation succeeded, nothing to return | `ApiResponse.noContent()` |
| General error — not found, unauthorized | `ErrorResponse.of(...)` |
| Validation failed — bad fields | `ErrorResponse.withFieldErrors(...)` |

The frontend always gets one of these two shapes. No surprises.

---

### Why `Instant` instead of a formatted date string?

`Instant` is a precise point in time — timezone-free, serialized in ISO 8601 format: `2025-01-15T10:23:45Z`. It travels well across systems and time zones without ambiguity. The frontend or a log aggregator can format it however it needs to. The API doesn't make that decision for them.

---

### The analogy

| Thing | Analogy |
|---|---|
| `of(...)` | A standard incident report — something went wrong, here's the summary |
| `withFieldErrors(...)` | The same report, but with a checklist attached — these are the specific items that failed inspection |
| `fieldErrors` map | The checklist — field name on the left, what's wrong with it on the right |
| `timestamp` | The time stamp on the report — when exactly did this happen |

---

### Final one-liner

> When something goes wrong, the frontend deserves the same consistency as when things go right — a predictable shape, a clear message, and when fields are the problem, exactly which ones and why.