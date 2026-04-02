# The `common/util/` Package — Why It Exists

## The Core Idea

`util/` is a home for **pure helper classes** — stateless, reusable
functions that multiple parts of the application need, but that don't
belong to any single domain.

It is different from `config/` in one important way:

| Package | Purpose |
|---|---|
| `config/` | Classes that **wire the application together** |
| `util/` | Classes that are **shared tools** used by many other classes |

---

## The Clearest Example: `JwtUtil.java`

`JwtUtil` does exactly five things:

- Generate an access token
- Generate a refresh token
- Verify a token's signature
- Extract the user ID from a token
- Check if a token is expired

These are pure functions. They take inputs, return outputs, hold no
state, and have no side effects. Now ask — where should this code live?

### Why not in `AuthService`?

`AuthService` needs to **generate** tokens at login. But `JwtFilter`
also needs to **verify** tokens on every incoming request. If the
verification logic lived inside `AuthService`, then `JwtFilter` would
have to inject and depend on `AuthService` just to call one function.

That's the wrong dependency. `AuthService` is a domain service — it
handles login, logout, registration. `JwtFilter` is infrastructure — it
intercepts HTTP requests. Infrastructure depending on a domain service
for a utility function creates an awkward, misleading coupling.

### Why not in `JwtFilter`?

Same problem in reverse. `AuthService` needs to generate tokens.
If the generation logic lived in `JwtFilter`, then `AuthService` would
depend on a filter class to do its core job. That makes even less sense.

### Why not in `AuthController`?

Controllers should contain no logic at all. They map HTTP to service
calls and nothing more.

### So where?

It needs to live somewhere both `AuthService` and `JwtFilter` can reach,
without depending on each other. A neutral location. That place is
`util/`.

```
JwtUtil
  ↑           ↑
AuthService  JwtFilter

Both depend on JwtUtil.
Neither depends on the other.
```

---

## What `JwtUtil.java` Looks Like

```java
@Component
public class JwtUtil {

    private final JwtConfig jwtConfig;  // injected from config/

    public String generateAccessToken(String userId, String role) {
        return Jwts.builder()
            .setSubject(userId)
            .claim("role", role)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getAccessTokenExpiry()))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
            .setSubject(userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshTokenExpiry()))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public String extractUserId(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecretKey().getBytes());
    }
}
```

Notice: no database calls, no business rules, no side effects. Pure
input-output functions. This is what belongs in `util/`.

---

## How Other Classes Use It

**`AuthService`** — at login, after verifying the password:
```java
String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getRole());
String refreshToken = jwtUtil.generateRefreshToken(user.getId());
```

**`JwtFilter`** — on every incoming request:
```java
String token  = extractTokenFromHeader(request);
boolean valid = jwtUtil.isTokenValid(token);
String userId = jwtUtil.extractUserId(token);
String role   = jwtUtil.extractRole(token);
```

Same `JwtUtil`, two completely different callers, no coupling between
the callers themselves.

---

## The Rule for What Belongs in `util/`

A class belongs in `util/` if it satisfies all three of these:

**1. Stateless** — it holds no data between calls. Every call is
independent. `JwtUtil` doesn't remember previous tokens. It just
processes what it receives.

**2. Reusable** — it is needed by more than one class. If only one class
ever uses it, the helper method can just live inside that class.

**3. Domain-neutral** — it doesn't belong to any single feature. JWT
operations are not an "auth" concept exclusively — they are used by the
security filter which sits outside the auth domain entirely.

If a helper function is stateless and reusable but only used within one
domain (e.g. a date formatter only used in `BookingService`), it can
live as a private method inside that service — it doesn't need to be
promoted to `util/`.

---

## What Could Grow in `util/` Over Time

As miniAgoda grows, other shared helpers might appear here:

```
common/util/
├── JwtUtil.java          ← token generation, verification, claim extraction
├── DateUtil.java         ← date range calculations (nights between check-in/out)
├── SlugUtil.java         ← generate URL-friendly hotel name slugs
└── PaginationUtil.java   ← build pagination metadata for list responses
```

Each one: stateless, reusable, domain-neutral.

---

## Full `common/util/` in Context

```
src/main/java/com/miniagoda/
├── common/
│   ├── config/
│   │   ├── SecurityConfig.java    ← installs JwtFilter, references JwtUtil indirectly
│   │   ├── JwtConfig.java         ← owns the secret key and expiry values
│   │   └── AppConfig.java
│   ├── exception/
│   ├── response/
│   └── util/
│       └── JwtUtil.java           ← uses JwtConfig, used by AuthService + JwtFilter
├── auth/
│   ├── AuthService.java           ← injects JwtUtil to generate tokens
│   └── JwtFilter.java             ← injects JwtUtil to verify tokens
└── ...
```

The dependency direction is clean:

```
AuthService  →  JwtUtil  ←  JwtFilter
                  ↑
              JwtConfig
```

`JwtUtil` depends on `JwtConfig` (for settings). Everything else depends
on `JwtUtil`. Nobody depends on anybody they shouldn't.

---

## Glossary

| Term | Meaning |
|---|---|
| Utility class | A stateless class of helper functions with no business logic of their own |
| Stateless | Holds no data between calls — every invocation is independent |
| Domain-neutral | Not specific to any one feature — usable across the whole application |
| `JwtUtil` | The utility class that generates, verifies, and reads JWT tokens |
| `JwtFilter` | The Spring filter that intercepts every request and calls `JwtUtil` to validate the token |
| Coupling | When one class depends on another — too much coupling makes code hard to change |

---

## One-Paragraph Summary

`util/` exists for stateless, reusable helper classes that are needed by
more than one part of the application and don't belong to any single
domain. The clearest example is `JwtUtil` — both `AuthService` (which
generates tokens at login) and `JwtFilter` (which verifies tokens on
every request) need JWT operations, but neither should depend on the
other. Putting the logic in `util/` gives both a shared tool without
creating a coupling between them. The rule is simple: if a helper is
stateless, used by more than one class, and doesn't belong to a specific
domain, it goes in `util/`. If only one class ever uses it, it stays
private inside that class.