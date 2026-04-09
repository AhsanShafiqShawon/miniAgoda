# SecurityErrorWriter — Code Prose

`com.miniagoda.common.util.SecurityErrorWriter`

---

## Overview

This class extracts one piece of repeated behaviour from the security layer and gives it a home.

The problem it solves is visible in `JwtAuthenticationFilter`: when a request fails authentication at the filter level, the filter cannot rely on `GlobalExceptionHandler` because it operates before the Spring MVC dispatcher that makes exception handling possible. It has to write the error response to the HTTP output stream directly. `JwtAuthenticationFilter` handled this with a private `writeUnauthorized` method — but any other filter or security entry point that needs to do the same thing would have to duplicate that logic. `SecurityErrorWriter` is the extraction of that pattern into a shared, injectable component.

It is annotated with `@Component` and depends on `ObjectMapper`, injected through the constructor, which handles the serialisation of `ErrorResponse` to JSON.

---

## `write(...)`

This method is the class's single responsibility: build an `ErrorResponse`, set the appropriate response metadata, and write the body.

It accepts the full set of values needed to construct a meaningful error — the status code, a short error label, a human-readable message, and the request itself from which the URI is extracted. It constructs an `ErrorResponse` using the `of` factory, sets the HTTP status and content type on the response, then serialises the body and writes it to the response writer.

The method's signature is deliberately general. Unlike `JwtAuthenticationFilter`'s private method, which was hardcoded to `401`, this one accepts any status code and error string, making it usable for `401 Unauthorized`, `403 Forbidden`, or any other error a security filter might need to surface. The caller decides what the error means; this class only decides how it is written.

# SecurityErrorWriter — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **shared rejection slip printer**.

When a security filter needs to reject a request — bad token, missing header, expired session — it can't use `GlobalExceptionHandler`. That handler only works inside the controller layer. Filters operate outside it. So without a shared utility, every filter would have to write its own response manually, duplicating the same four lines every time.

`SecurityErrorWriter` extracts those four lines into one reusable method that any filter can call.

| Concept | What it is |
|---|---|
| `SecurityErrorWriter` | A shared utility for writing JSON error responses directly to the HTTP response |
| `write(...)` | The one method — builds an `ErrorResponse`, sets the status, and writes the JSON body |

---

## 🧩 Step-by-Step in Plain Terms

### 1. The constructor — one dependency

```java
public SecurityErrorWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
}
```

`ObjectMapper` is Jackson's JSON serializer. It's the only tool this class needs — everything else is built from what the caller passes in. Spring injects it automatically.

---

### 2. `write(...)` — one method, four lines of work

```java
public void write(HttpServletResponse response,
                  HttpServletRequest request,
                  int status,
                  String error,
                  String message) throws IOException {
```

The caller passes in everything needed to build and send the response: the raw `response` and `request` objects, the HTTP status code, a short error label, and a human-readable message. The method does the rest.

**Step 1 — Build the response body**

```java
ErrorResponse body = ErrorResponse.of(status, error, message, request.getRequestURI());
```

`request.getRequestURI()` pulls the path from the incoming request — `/api/hotels/42`, for example — so the error response includes exactly which endpoint was called when the failure happened. Everything is assembled into the standard `ErrorResponse` shape.

**Step 2 — Set the HTTP status**

```java
response.setStatus(status);
```

The status code is written directly to the HTTP response. `401`, `403`, whatever the caller passed in.

**Step 3 — Set the content type**

```java
response.setContentType(MediaType.APPLICATION_JSON_VALUE);
```

Tells the client to expect JSON. Without this, the client has no way of knowing how to parse the response body.

**Step 4 — Write the body**

```java
response.getWriter().write(objectMapper.writeValueAsString(body));
```

`ObjectMapper` serializes the `ErrorResponse` object into a JSON string and writes it directly to the response output stream. The client receives a properly formatted JSON error body.

---

## 🔥 One-Line Summary

> A small shared utility that any security filter can call to write a consistent JSON error response directly to the HTTP output — bypassing the controller layer entirely.

---

## 💡 Deep Dive: Why does this class need to exist at all?

The answer goes back to where filters live in the request lifecycle.

---

### The problem: filters are outside `GlobalExceptionHandler`'s reach

```
Incoming request
        ↓
Filter layer  ← JwtAuthenticationFilter lives here
        ↓
DispatcherServlet
        ↓
GlobalExceptionHandler  ← only catches exceptions from here down
        ↓
Controller
```

`GlobalExceptionHandler` is wired to Spring MVC's `DispatcherServlet`. It catches exceptions that bubble up from controllers and services. But filters run before the `DispatcherServlet` is even involved. If a filter throws an exception, it doesn't reach `GlobalExceptionHandler` — it gets handled by the servlet container instead, which produces an inconsistent, often unformatted error response.

So filters can't throw. They have to write the response themselves.

---

### The problem without `SecurityErrorWriter`

Before this utility existed, `JwtAuthenticationFilter` wrote responses inline:

```java
// Inside JwtAuthenticationFilter — duplicated logic
ErrorResponse body = ErrorResponse.of(401, "Unauthorized", message, request.getRequestURI());
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
response.setContentType(MediaType.APPLICATION_JSON_VALUE);
response.getWriter().write(objectMapper.writeValueAsString(body));
```

That's fine for one filter. But as the app grows — a rate-limiting filter, an API key filter, a maintenance mode filter — every new filter that needs to reject a request duplicates those same four lines. Change the response format once and you're updating it in five places.

---

### The solution: one shared writer

With `SecurityErrorWriter`, every filter does the same thing in one line:

```java
// Inside any filter
securityErrorWriter.write(response, request, 401, "Unauthorized", "Missing or malformed Authorization header.");
```

The four-line pattern lives in one place. If `ErrorResponse` ever changes shape, or the content type needs updating, or a timestamp format shifts — one class to update, every filter benefits.

---

### How it connects to the classes around it

```
Any security filter
        ↓
SecurityErrorWriter.write(...)
        ↓
ErrorResponse.of(...)   ← same shape as GlobalExceptionHandler uses
        ↓
ObjectMapper serializes to JSON
        ↓
Written directly to HttpServletResponse
```

The frontend receives the exact same `ErrorResponse` structure whether the error came from a filter or from a controller. Consistent. Predictable. No surprises.

---

### The analogy

| Thing | Analogy |
|---|---|
| `GlobalExceptionHandler` | The customer service desk inside the building — handles complaints that make it through the door |
| Security filters | The security guards at the entrance — they stop people before they get inside |
| `SecurityErrorWriter` | A standard-issue rejection slip template the guards all carry — same format, same information, every time |
| Without `SecurityErrorWriter` | Each guard writing rejection slips in their own handwriting, in their own format, sometimes forgetting fields |

---

### Final one-liner

> Filters can't use the exception handler — so `SecurityErrorWriter` gives every filter a single, consistent way to write a proper JSON error response without duplicating the same four lines across the codebase.