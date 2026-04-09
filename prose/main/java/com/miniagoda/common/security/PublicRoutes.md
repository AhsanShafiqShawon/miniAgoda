# PublicRoutes — Code Prose

`com.miniagoda.common.security.PublicRoutes`

---

## Overview

This class is a single, authoritative list of every route in the application that a caller may access without a token.

It is a utility class in the strictest sense: a `final` class with a private constructor, no instance state, and no methods beyond what is needed to prevent instantiation. It cannot be extended and cannot be constructed. Its only member is a constant.

The alternative — scattering exemptions across security configuration, filter logic, or individual controllers — would make it easy to lose track of which routes are actually public. Collecting them here means the answer to "what can an unauthenticated caller reach?" is always one file away.

---

## `MATCHERS`

This constant is a two-dimensional array where each entry pairs an HTTP method with a path pattern.

The method matters. `POST /api/auth/**` is public because login and registration must be reachable without a token — that is how a token is obtained in the first place. But a `DELETE` or `PUT` to the same path prefix should not automatically be public just because the prefix is. By requiring both dimensions, the array makes each exemption precise: it is not a path that is public, it is a specific verb on a path.

The path patterns use Ant-style wildcards. `**` matches any number of path segments, so `/api/hotels/**` covers `/api/hotels/`, `/api/hotels/123`, `/api/hotels/123/rooms`, and anything else beneath that prefix. This is appropriate for read-only browsing routes where the intent is to allow unauthenticated discovery across the entire resource hierarchy.

Whatever security configuration consumes this constant — a filter deciding whether to require a token, a Spring Security rule set — reads from here rather than defining its own list. The exemptions are declared once and applied everywhere.

# PublicRoutes — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **guest list posted at the door**.

Not every route in the app requires authentication. Searching for hotels, browsing listings, logging in — these need to be accessible to anyone. `PublicRoutes` is a single, central place that declares exactly which routes those are. Everything not on this list requires a valid JWT.

| Concept | What it is |
|---|---|
| `PublicRoutes` | A constants class that holds the list of routes that don't require authentication |
| `MATCHERS` | A 2D array of method + path pairs — each row is one public route |

---

## 🧩 Step-by-Step in Plain Terms

### 1. `public final class` — this class is never extended

`final` means nothing can subclass `PublicRoutes`. There's no behaviour to inherit here — this is a constants holder, not a base class. Marking it `final` signals that clearly.

---

### 2. `private PublicRoutes()` — this class is never instantiated

```java
private PublicRoutes() {}
```

The constructor is private. Nobody can do `new PublicRoutes()`. The class exists purely to hold the `MATCHERS` constant — you access it directly as `PublicRoutes.MATCHERS`, never through an instance. This is the standard Java pattern for a utility or constants class.

---

### 3. `MATCHERS` — the list itself

```java
public static final String[][] MATCHERS = {
    {"POST", "/api/auth/**"},
    {"GET",  "/api/hotels/**"},
    {"GET",  "/api/search/**"},
};
```

A 2D array where each row is a pair: an HTTP method and a path pattern.

| Row | Method | Path | What it covers |
|---|---|---|---|
| 0 | `POST` | `/api/auth/**` | Login, register, token refresh |
| 1 | `GET` | `/api/hotels/**` | Browsing hotel listings |
| 2 | `GET` | `/api/search/**` | Searching by city, date, guests |

The `**` wildcard means "this path and everything under it." `/api/auth/**` matches `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh` — any path that starts with `/api/auth/`.

The method matters. `GET /api/hotels/**` is public — anyone can browse. But `POST /api/hotels/**` is not listed — creating a hotel requires authentication. Same base path, different verb, different access level.

---

## 🔥 One-Line Summary

> A constants class that holds every route the app allows without a JWT — one place to look, one place to update, used by both the filter and the security config.

---

## 💡 Deep Dive: Why centralise this in its own class?

Before `PublicRoutes` existed, the same list of paths would need to live in two places.

---

### The two places that need it

**`JwtAuthenticationFilter`** — before checking the token, the filter needs to know if the current route is public. If it is, skip the check entirely and let the request through. If not, validate the token.

**`SecurityConfig`** — when wiring up Spring Security's route-level access rules, the config needs to know which routes to mark as `permitAll()` and which to mark as `authenticated()`.

Without a shared constant, both classes define their own list. They drift apart. Someone adds a new public route to the filter and forgets the config. A route becomes publicly accessible from the filter's perspective but blocked by the security config, or the reverse — authenticated from the config's perspective but the filter skips the token check entirely. Both are bugs.

---

### With `PublicRoutes`, both classes read from the same source

```java
// In JwtAuthenticationFilter
for (String[] matcher : PublicRoutes.MATCHERS) {
    if (request.getMethod().equals(matcher[0]) &&
        pathMatcher.match(matcher[1], request.getRequestURI())) {
        filterChain.doFilter(request, response);
        return;
    }
}

// In SecurityConfig
for (String[] matcher : PublicRoutes.MATCHERS) {
    auth.requestMatchers(
        new AntPathRequestMatcher(matcher[1], matcher[0])
    ).permitAll();
}
```

One update to `MATCHERS`. Both classes reflect it immediately. No drift.

---

### Why method + path, not just path?

Listing just the path — `/api/hotels/**` — would make every HTTP method on that path public. `GET /api/hotels/**` for browsing is fine. But `POST /api/hotels/**` for creating a hotel, `PUT /api/hotels/**` for updating, `DELETE /api/hotels/**` for removing — those should require authentication.

Pairing the method with the path gives precise control. Public means this specific verb on this specific path. Not the whole path.

---

### Adding a new public route

When a new endpoint needs to be publicly accessible, one line changes:

```java
public static final String[][] MATCHERS = {
    {"POST", "/api/auth/**"},
    {"GET",  "/api/hotels/**"},
    {"GET",  "/api/search/**"},
    {"GET",  "/api/promotions/**"},  // ← added here
};
```

The filter and the security config both pick it up automatically. No hunting through two or three classes to make the same change in multiple places.

---

### The analogy

| Thing | Analogy |
|---|---|
| `PublicRoutes.MATCHERS` | The guest list posted at the front desk — these people get in without an ID check |
| Method + path pair | Not just a name on the list — a name and a specific door. "Guests can enter through the main entrance, not the staff corridor" |
| Private constructor | The list is printed on the wall — you read it, you don't carry a copy of it around |
| Two classes reading the same list | Both the door guard and the front desk use the same posted list — they never disagree on who gets in |

---

### Final one-liner

> `PublicRoutes` exists so the filter and the security config never disagree on which routes are public — one list, two consumers, no drift.