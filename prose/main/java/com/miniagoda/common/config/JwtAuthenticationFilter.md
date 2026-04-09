# JwtAuthenticationFilter — Code Prose

`com.miniagoda.common.config.JwtAuthenticationFilter`

---

## Overview

This class is the gatekeeper that runs before every request, inspecting it for a valid JWT and deciding whether the caller is allowed to proceed.

It extends `OncePerRequestFilter`, a Spring base class that guarantees the filter executes exactly once per request regardless of how many times the request is dispatched internally. This matters for security filters: running authentication logic more than once per request would be wasteful at best and inconsistent at worst.

It is annotated with `@Component`, registering it as a Spring-managed bean. It depends on two collaborators injected through the constructor. `JwtDecoder` handles the cryptographic work of verifying and parsing a token — it knows the signing key, validates the signature, and checks the expiry. `ObjectMapper` is Jackson's JSON serialiser, used here to write error responses directly to the HTTP response body when authentication fails. The filter cannot delegate to `GlobalExceptionHandler` in these cases because filters run outside the dispatcher servlet layer where Spring's exception handling machinery operates; it must write the response itself.

---

## `doFilterInternal`

This method is the filter's single entry point, and it follows a strict sequence: validate the header, decode the token, establish the authentication, and pass the request on.

**Header check.** The first thing it does is read the `Authorization` header. If the header is absent or does not begin with `"Bearer "`, the request is rejected immediately with a `401`. There is nothing to decode and no reason to continue. The constant `BEARER_PREFIX` keeps this check readable and ensures the prefix string is defined in exactly one place.

**Token decoding.** Once the raw token string is extracted by stripping the prefix, it is handed to `jwtDecoder.decode`. This is where the cryptographic verification happens — signature validation, expiry check, issuer check — all opaquely, inside the decoder. If the token is invalid for any reason, `JwtDecoder` throws a `JwtException`, which the filter catches and converts into a `401` with the message `"Invalid or expired token."` The two rejection paths — bad header, bad token — produce the same status code but different messages, giving a client or developer just enough information to distinguish between them.

**Authentication establishment.** If decoding succeeds, the filter extracts the `subject` claim as the user ID and the custom `role` claim from the verified `Jwt`. It constructs a `UsernamePasswordAuthenticationToken` — Spring Security's standard representation of an authenticated principal — with the user ID as the principal, no credentials, and a single `GrantedAuthority` derived from the role. The role is prefixed with `"ROLE_"` because that is the convention Spring Security expects when making role-based access decisions downstream.

This token is placed into the `SecurityContextHolder`, which is the thread-local store Spring Security reads when evaluating whether an authenticated user has access to a given resource. From this point forward in the request lifecycle, the caller is known.

**Continuation.** With the security context populated, `filterChain.doFilter` passes the request to the next filter in the chain and eventually to the controller. If authentication failed at any point before this line, the method has already returned after writing the error response, and the chain is never continued.

---

## `writeUnauthorized`

This private method handles the mechanics of writing a `401` response directly to the HTTP output stream.

It constructs an `ErrorResponse` using the `of` factory, sets the response status and content type, then serialises the body to JSON using `ObjectMapper` and writes it to the response writer. The content type is set explicitly to `application/json` so the client knows how to interpret the body.

The method exists because error handling at the filter level bypasses the normal Spring MVC machinery. `GlobalExceptionHandler` only intercepts exceptions that reach the dispatcher servlet; a request rejected here never gets that far. This method is the filter's own fallback, producing the same `ErrorResponse` shape the rest of the application uses so that authentication failures are indistinguishable in structure from any other error a client might receive.

# JwtAuthenticationFilter — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **ID checker at the door**.

Every single request that comes into the app passes through this filter before it reaches any controller. The filter has one job: look for a JWT token, validate it, and if it's good — tell the rest of the app who this request belongs to. If anything is wrong, stop the request right here and send back a `401`.

This is the missing piece that `SecurityConfig` was waiting for.

| Concept | What it is |
|---|---|
| `JwtAuthenticationFilter` | The filter that checks every request for a valid JWT |
| `OncePerRequestFilter` | A Spring base class that guarantees this filter runs exactly once per request |
| `JwtDecoder` | The tool that validates and unpacks the token |
| `SecurityContextHolder` | Where Spring stores the identity of the current request |

---

## 🧩 Step-by-Step in Plain Terms

### 1. `extends OncePerRequestFilter` — why this base class?

Spring's filter chain can sometimes call a filter more than once per request in complex scenarios — forwarded requests, error dispatches, and so on. `OncePerRequestFilter` is a Spring base class that prevents that. It guarantees `doFilterInternal` runs exactly once, no matter what. For an authentication filter this matters — you don't want to check the token twice or accidentally authenticate a request twice.

---

### 2. The constructor — two dependencies come in

**`JwtDecoder`** — this is the token validator. You hand it a raw token string and it either returns a decoded `Jwt` object or throws a `JwtException`. It verifies the signature, checks the expiry, and unpacks the claims all in one call.

**`ObjectMapper`** — Jackson's JSON serializer. The filter needs this because it sits below the `GlobalExceptionHandler`. When this filter rejects a request, the exception handler is never involved — the filter writes the response directly. `ObjectMapper` turns the `ErrorResponse` into a JSON string so it can be written to the response body manually.

---

### 3. `doFilterInternal` — the main logic, step by step

This is the method Spring calls for every incoming request. It runs the full ID check sequence.

---

**Step 1 — Read the Authorization header**

```java
String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
    writeUnauthorized(response, request, "Missing or malformed Authorization header.");
    return;
}
```

The token is expected in the `Authorization` header, formatted as:

```
Authorization: Bearer eyJhbGci...
```

If the header is missing entirely, or if it doesn't start with `"Bearer "`, the request is rejected immediately. No token means no identity. The filter writes a `401` and returns — the `return` statement is important. It stops the filter chain dead. The request never reaches a controller.

---

**Step 2 — Extract the raw token string**

```java
String token = authHeader.substring(BEARER_PREFIX.length());
```

The `"Bearer "` prefix is stripped off, leaving just the token itself. `BEARER_PREFIX` is defined as the constant `"Bearer "` — seven characters including the space — so `substring(7)` gives the raw JWT string.

---

**Step 3 — Decode and validate the token**

```java
Jwt jwt;
try {
    jwt = jwtDecoder.decode(token);
} catch (JwtException ex) {
    writeUnauthorized(response, request, "Invalid or expired token.");
    return;
}
```

The token string is handed to `JwtDecoder`. This is where the real validation happens. `JwtDecoder` checks three things in one call:

1. Is the signature valid? — was this token actually signed by this app using the secret key?
2. Has the token expired? — is the current time past the `expiresAt` claim?
3. Is the structure well-formed? — is this actually a valid JWT?

If any of these fail, `JwtDecoder` throws a `JwtException`. The filter catches it, writes a `401`, and returns. The reason isn't specified in detail — "Invalid or expired token" covers all failure cases without revealing which check failed.

If decoding succeeds, `jwt` holds the fully validated, decoded token and everything inside it.

---

**Step 4 — Extract the user's identity from the token**

```java
String userId = jwt.getSubject();
String role = jwt.getClaimAsString("role");
```

Two claims are pulled out of the decoded token:

`getSubject()` returns the `sub` claim — the user ID that was stamped into the token when it was created in `JwtUtil`.

`getClaimAsString("role")` returns the custom `role` claim — `"GUEST"`, `"HOST"`, or `"ADMIN"` — also stamped in at creation time.

---

**Step 5 — Build an authentication object and load it into the security context**

```java
UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        userId,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role))
);

SecurityContextHolder.getContext().setAuthentication(authentication);
```

This is the moment the request gets an identity.

`UsernamePasswordAuthenticationToken` is Spring Security's standard container for a verified identity. It holds three things:

| Argument | What it is |
|---|---|
| `userId` | The principal — who this request belongs to |
| `null` | The credentials — not needed, the token already proved identity |
| `List.of(new SimpleGrantedAuthority("ROLE_" + role))` | The authorities — what this user is allowed to do |

The role is prefixed with `"ROLE_"` because that's the convention Spring Security expects. A role stored as `"HOST"` in the token becomes `"ROLE_HOST"` in the authority. When `SecurityConfig` later checks `.hasRole("HOST")`, Spring prepends `"ROLE_"` internally — so the two match.

`SecurityContextHolder.getContext().setAuthentication(authentication)` stores this identity for the duration of the request. Any code downstream — controllers, services — can call `SecurityContextHolder.getContext().getAuthentication()` and get back the user ID and role.

---

**Step 6 — Pass the request down the chain**

```java
filterChain.doFilter(request, response);
```

Identity confirmed, context loaded. The request is allowed to continue. `filterChain.doFilter` hands the request to the next filter in the chain, and eventually it reaches the controller.

---

### 4. `writeUnauthorized` — why the filter writes responses directly

```java
private void writeUnauthorized(HttpServletResponse response,
                                HttpServletRequest request,
                                String message) throws IOException {
    ErrorResponse body = ErrorResponse.of(401, "Unauthorized", message, request.getRequestURI());
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(body));
}
```

This method exists because the filter operates outside the reach of `GlobalExceptionHandler`.

`GlobalExceptionHandler` intercepts exceptions that escape from controllers. But this filter runs before any controller is involved. If it throws an exception, the exception handler never sees it — Spring's filter error handling kicks in instead, and the response ends up malformed or inconsistent.

So instead of throwing, the filter writes the response itself. It sets the status to `401`, sets the content type to JSON, serializes an `ErrorResponse` using `ObjectMapper`, and writes it directly to the response body. The same `ErrorResponse` shape the rest of the app uses — consistent, clean, no surprises for the frontend.

---

## 🔥 One-Line Summary

> Every request passes through this filter — it checks for a valid JWT, extracts the user's identity, loads it into the security context, and rejects anything that can't prove who it is before a single controller method runs.

---

## 💡 Deep Dive: What is the security context and why does it matter?

`SecurityContextHolder` is Spring Security's thread-local storage for the current request's identity. Think of it as a sticky note attached to the current thread that says "this request belongs to user X with role Y."

---

### How downstream code uses it

Once the filter loads the authentication, any service can ask:

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String userId = (String) auth.getPrincipal();
```

Without the filter doing this work, `auth` would be `null` for every request. Controllers and services would have no way of knowing who they're serving.

---

### It's cleared automatically

Spring Security clears the context at the end of every request. The sticky note is removed. The next request starts clean. There's no risk of one user's identity bleeding into another request — which is exactly what you'd expect from a stateless system.

---

### The full request journey with this filter in place

```
Incoming request
        ↓
JwtAuthenticationFilter
   ├── No header?         → 401, stop
   ├── Bad token?         → 401, stop
   └── Valid token?       → load identity into SecurityContextHolder
        ↓
SecurityConfig rules
   ├── Route requires ADMIN, user is GUEST? → 403, stop
   └── Route allowed for this role?         → continue
        ↓
Controller
        ↓
Service (can read userId and role from SecurityContextHolder)
```

---

### Why `null` for credentials?

```java
new UsernamePasswordAuthenticationToken(userId, null, authorities)
```

In a traditional username/password flow, the credentials field holds the password. Here, the JWT itself was the proof of identity — `JwtDecoder` already validated it. There's nothing left to verify. Passing `null` signals that credentials have already been consumed and don't need to be stored.

---

### The analogy

| Thing | Analogy |
|---|---|
| `JwtAuthenticationFilter` | The ID checker at the venue entrance |
| Missing header | Arriving with no ID at all — turned away immediately |
| Invalid/expired token | Arriving with a fake or expired ID — turned away immediately |
| `JwtDecoder` | The scanner that checks if the ID is genuine |
| `SecurityContextHolder` | The wristband put on after passing the check — staff inside can see it and know who you are |
| `filterChain.doFilter` | The ID checker waving you through — you're good to go |
| `writeUnauthorized` | The ID checker handing you a printed rejection slip rather than just pointing vaguely at the exit |

---

### Final one-liner

> The filter is the gatekeeper — it runs before everything else, validates the token, stamps the request with an identity, and either lets it through or shuts it down with a clean, consistent error response.