# JwtAuthenticationFilter — Code Prose

`com.miniagoda.common.filter.JwtAuthenticationFilter`

---

## Overview

This class is the gatekeeper that runs before every request, inspecting it for a valid JWT and deciding whether the caller is allowed to proceed.

It extends `OncePerRequestFilter`, a Spring base class that guarantees the filter executes exactly once per request regardless of how many times the request is dispatched internally. This matters for security filters: running authentication logic more than once per request would be wasteful at best and inconsistent at worst.

It is annotated with `@Component`, registering it as a Spring-managed bean. It depends on two collaborators injected through the constructor. `JwtDecoder` handles the cryptographic work of verifying and parsing a token — it knows the signing key, validates the signature, and checks the expiry. `SecurityErrorWriter` writes structured JSON error responses directly to the HTTP response body when authentication fails. The filter cannot delegate to `GlobalExceptionHandler` in these cases because filters run outside the dispatcher servlet layer where Spring's exception handling machinery operates; it must write the response itself, and `SecurityErrorWriter` encapsulates exactly that responsibility.

---

## `shouldNotFilter`

Before `doFilterInternal` is even called, Spring checks `shouldNotFilter`. If it returns `true`, the filter skips the request entirely and passes it straight to the next filter in the chain.

The method iterates over `PublicRoutes.MATCHERS` — the same shared constant `SecurityConfig` uses — and returns `true` if the request's method and path match any entry. The path comparison strips the `/**` wildcard suffix before calling `startsWith`, so a path like `/api/auth/login` correctly matches the `/api/auth/**` pattern. These routes are intentionally unauthenticated — no token should be required to log in, register, or browse public listings. Skipping the filter here rather than inside `doFilterInternal` keeps the main logic clean and avoids performing any header reads on requests that will never carry a token. Using `PublicRoutes.MATCHERS` also means the set of skipped routes stays in sync with what `SecurityConfig` permits — there is no separate list to maintain.

---

## `doFilterInternal`

This method is the filter's single entry point for all non-public requests, and it follows a strict sequence: validate the header, decode the token, validate the claims, establish the authentication, and pass the request on.

**Header check.** The first thing it does is read the `Authorization` header. If the header is absent or does not begin with `"Bearer "`, the request is rejected immediately with a `401`. There is nothing to decode and no reason to continue. The constant `BEARER_PREFIX` keeps this check readable and ensures the prefix string is defined in exactly one place.

**Token decoding.** Once the raw token string is extracted by stripping the prefix, it is handed to `jwtDecoder.decode`. This is where the cryptographic verification happens — signature validation, expiry check, issuer check — all opaquely, inside the decoder. If the token is invalid for any reason, `JwtDecoder` throws a `JwtException`, which the filter catches and converts into a `401` with the message `"Invalid or expired token."` The two rejection paths — bad header, bad token — produce the same status code but different messages, giving a client or developer just enough information to distinguish between them.

**Claims validation.** After decoding succeeds, the `subject` claim is read as the user ID and the custom `role` claim is extracted. If `role` is `null` — meaning the token was structurally valid but was issued without the required claim — the request is rejected with a `401` and the message `"Token is missing required claims."` This guards against tokens that pass signature verification but lack the data needed to establish an identity.

**Authentication establishment.** With both claims in hand, the filter constructs a `UsernamePasswordAuthenticationToken` — Spring Security's standard representation of an authenticated principal — with the user ID as the principal, no credentials, and a single `GrantedAuthority` derived from the role. The role is prefixed with `"ROLE_"` because that is the convention Spring Security expects when making role-based access decisions downstream.

This token is placed into the `SecurityContextHolder`, which is the thread-local store Spring Security reads when evaluating whether an authenticated user has access to a given resource. From this point forward in the request lifecycle, the caller is known.

**Continuation.** With the security context populated, `filterChain.doFilter` passes the request to the next filter in the chain and eventually to the controller. If authentication failed at any point before this line, the method has already returned after writing the error response, and the chain is never continued.

---

## `writeUnauthorized`

This private helper exists solely to keep the rejection calls inside `doFilterInternal` readable. It delegates directly to `securityErrorWriter.write`, passing the response, request, `401` status, the `"Unauthorized"` label, and the caller-supplied message. `SecurityErrorWriter` owns the mechanics of setting headers and serialising the body — this method contributes nothing beyond routing to it.

The method exists because error handling at the filter level bypasses the normal Spring MVC machinery. `GlobalExceptionHandler` only intercepts exceptions that reach the dispatcher servlet; a request rejected here never gets that far. `SecurityErrorWriter` is the filter's own fallback, producing the same `ErrorResponse` shape the rest of the application uses so that authentication failures are indistinguishable in structure from any other error a client might receive.

# JwtAuthenticationFilter — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **ID checker at the door**.

Every request that comes into the app — except for deliberately public routes — passes through this filter before it reaches any controller. The filter has one job: look for a JWT token, validate it, and if it's good — tell the rest of the app who this request belongs to. If anything is wrong, stop the request right here and send back a `401`.

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

**`SecurityErrorWriter`** — the dedicated error response writer. The filter needs this because it sits below the `GlobalExceptionHandler`. When this filter rejects a request, the exception handler is never involved — the filter writes the response directly. `SecurityErrorWriter` owns the mechanics of setting the status, content type, and JSON body, so the filter can reject a request in one call without caring about serialisation details.

---

### 3. `shouldNotFilter` — skipping public routes entirely

```java
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    String method = request.getMethod();
    for (String[] matcher : PublicRoutes.MATCHERS) {
        if (method.equals(matcher[0]) && path.startsWith(matcher[1].replace("/**", ""))) {
            return true;
        }
    }
    return false;
}
```

Before the main logic even runs, Spring checks `shouldNotFilter`. If it returns `true`, the filter is skipped for that request — `doFilterInternal` is never called.

Rather than hardcoding path strings here, the method loops over `PublicRoutes.MATCHERS` — the same constant `SecurityConfig` uses to register `permitAll` rules. For each entry it checks whether the request method and path prefix match. The `/**` wildcard is stripped before `startsWith` so that a concrete path like `/api/auth/login` correctly matches the `/api/auth/**` pattern.

**Why the loop instead of hardcoded conditions?**

The list of public routes needs to be consistent in two places: `SecurityConfig` (what to allow) and `JwtAuthenticationFilter` (what to skip). A separate hardcoded list in each class is a maintenance trap — add a new public route in one place and forget the other, and the filter will reject requests that Spring would have permitted. `PublicRoutes.MATCHERS` is the single source of truth. Update it once and both places stay in sync automatically.

---

### 4. `doFilterInternal` — the main logic, step by step

This is the method Spring calls for every request that wasn't skipped. It runs the full ID check sequence.

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

**Step 4 — Extract and validate the claims**

```java
String userId = jwt.getSubject();
String role = jwt.getClaimAsString("role");

if (role == null) {
    writeUnauthorized(response, request, "Token is missing required claims.");
    return;
}
```

Two claims are pulled out of the decoded token:

`getSubject()` returns the `sub` claim — the user ID that was stamped into the token when it was created.

`getClaimAsString("role")` returns the custom `role` claim — `"GUEST"`, `"HOST"`, or `"ADMIN"`.

If `role` is `null`, the token passed signature verification but was issued without the data needed to establish an identity. This is treated as a `401` — the token is technically valid but unusable. A well-formed token from this application will always carry a role, so a missing one is a sign of a token that didn't come from here.

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

### 5. `writeUnauthorized` — why the filter writes responses directly

```java
private void writeUnauthorized(HttpServletResponse response,
                               HttpServletRequest request,
                               String message) throws IOException {
    securityErrorWriter.write(response, request, 401, "Unauthorized", message);
}
```

This method exists because the filter operates outside the reach of `GlobalExceptionHandler`.

`GlobalExceptionHandler` intercepts exceptions that escape from controllers. But this filter runs before any controller is involved. If it throws an exception, the exception handler never sees it — Spring's filter error handling kicks in instead, and the response ends up malformed or inconsistent.

So instead of throwing, the filter rejects the request by delegating to `SecurityErrorWriter`, which owns the mechanics of setting the status, content type, and writing the JSON body. The same `ErrorResponse` shape the rest of the app uses — consistent, clean, no surprises for the frontend. `writeUnauthorized` itself is just a thin wrapper that keeps the three rejection call sites inside `doFilterInternal` readable.

---

## 🔥 One-Line Summary

> Every non-public request passes through this filter — it checks for a valid JWT, validates its claims, extracts the user's identity, loads it into the security context, and rejects anything that can't prove who it is before a single controller method runs.

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
shouldNotFilter?
   ├── Public route?  → skip filter, continue chain
   └── Protected route? → run doFilterInternal
        ↓
doFilterInternal
   ├── No/bad header?           → 401, stop
   ├── Invalid/expired token?   → 401, stop
   ├── Missing role claim?      → 401, stop
   └── Valid token + claims?    → load identity into SecurityContextHolder
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
| `shouldNotFilter` | The side door marked "staff free entry" — certain routes bypass the check entirely |
| Missing header | Arriving with no ID at all — turned away immediately |
| Invalid/expired token | Arriving with a fake or expired ID — turned away immediately |
| Missing role claim | Arriving with a real ID that has the name blacked out — can't let you in |
| `JwtDecoder` | The scanner that checks if the ID is genuine |
| `SecurityContextHolder` | The wristband put on after passing the check — staff inside can see it and know who you are |
| `filterChain.doFilter` | The ID checker waving you through — you're good to go |
| `writeUnauthorized` | The ID checker handing you off to a dedicated rejection desk — one place handles all turnaways with the same printed slip |

---

### Final one-liner

> The filter is the gatekeeper — it skips public routes, validates the token and its claims for everything else, stamps the request with an identity, and either lets it through or shuts it down with a clean, consistent error response.