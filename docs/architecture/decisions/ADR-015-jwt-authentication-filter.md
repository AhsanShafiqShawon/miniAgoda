# ADR-015: JwtAuthenticationFilter Placement and Responsibility

## Status
Accepted

## Context

miniAgoda uses `spring-security-oauth2-jose` for JWT handling (ADR-014).
Under this library, token validation is delegated entirely to Spring
Security's built-in `JwtDecoder` bean — no hand-written validation logic
is required.

During the initial project layout, a filter class was sketched as
`JwtAuthFilter` and placed tentatively inside `auth/`. Two questions were
left open:

**Question 1 — Name.**
`JwtAuthFilter` contains `Auth`, which implies the class belongs to the
`auth/` feature package. However, the filter runs on every HTTP request
across the entire application — it is not scoped to authentication
endpoints. The name creates a misleading association.

**Question 2 — Package.**
`auth/` is a feature package. Feature packages contain the controller,
service, repository, DTOs, and entities for one domain area. A filter
that intercepts all requests is infrastructure, not a feature. Placing
it in `auth/` means a cross-cutting concern lives inside a feature
boundary, which violates the principle that motivated the feature-based
structure in the first place (ADR-002).

Two candidate homes inside `common/` were considered:

**`common/util/`** holds stateless helper classes — pure functions that
take an input and return an output with no side effects. A filter is
not stateless. It holds injected dependencies, participates in the
Spring bean lifecycle, and is registered into the Security filter chain.
It is not a utility.

**`common/config/`** holds classes that wire the application together —
`SecurityConfig`, `JwtConfig`, `AppConfig`. `SecurityConfig` is the
class that installs the filter into the chain via
`http.addFilterBefore(...)`. The filter and the class that registers it
belong together.

## Decision

Rename the class to `JwtAuthenticationFilter` and place it in
`common/config/`.

```
common/
└── config/
    ├── AppConfig.java
    ├── SecurityConfig.java          ← registers the filter
    ├── JwtConfig.java
    └── JwtAuthenticationFilter.java ← lives here
```

**On naming:** `JwtAuthenticationFilter` follows Spring Security's own
naming convention — `UsernamePasswordAuthenticationFilter`,
`BasicAuthenticationFilter`, `OncePerRequestFilter`. Removing `Auth`
shorthand in favour of `Authentication` makes the class read naturally
alongside the framework classes it extends and interacts with. The
`Auth` prefix is dropped because it carries a feature-package
connotation; `Authentication` describes the technical role without
implying feature ownership.

**On responsibility:** Under `spring-security-oauth2-jose` (ADR-014),
`JwtAuthenticationFilter` does not validate tokens. Spring Security's
`JwtDecoder` bean handles validation automatically once wired into the
filter chain via `SecurityConfig`. The filter's responsibility is
narrower than it would be under `jjwt`:

| Responsibility | Owner |
|---|---|
| Token generation | `JwtUtil` (generation-only) |
| Token signature verification | `JwtDecoder` bean (Spring Security) |
| Token expiry check | `JwtDecoder` bean (Spring Security) |
| Extracting claims from validated token | `JwtAuthenticationFilter` |
| Populating `SecurityContext` | `JwtAuthenticationFilter` |

This is a non-obvious split. Developers accustomed to `jjwt`-based
tutorials expect a single filter class that both validates and extracts.
Under this approach, the filter receives an already-validated `Jwt`
object from the framework and only needs to read its claims.

## Consequences

**Positive:**
- `common/config/` is cohesive — `SecurityConfig` and
  `JwtAuthenticationFilter` live together because one installs the other
- The name `JwtAuthenticationFilter` removes the `auth/` feature
  association and aligns with Spring Security's own naming conventions
- The filter's narrow responsibility (claims extraction and
  `SecurityContext` population only) is consistent with ADR-014's
  decision to delegate validation to the framework
- New developers can find the filter immediately — it lives next to the
  security configuration that registers it

**Negative:**
- `JwtAuthenticationFilter` doing less than a typical filter (no
  validation logic) may surprise developers expecting a full
  validate-and-extract pattern
- The `common/config/` package now mixes bean-definition classes
  (`AppConfig`, `JwtConfig`) with a filter class
  (`JwtAuthenticationFilter`) — they are different kinds of
  infrastructure, though both are wiring concerns

## Alternatives Considered

- **Keep in `auth/`**: The class name and feature package would both
  imply auth-feature ownership. Cross-cutting infrastructure should not
  live inside a feature boundary. Rejected.
- **Move to `common/util/`**: Utilities are stateless helpers. A filter
  holds dependencies and participates in the bean lifecycle. The
  category is wrong. Rejected.
- **Move to a new `common/filter/` package**: Defensible — it separates
  filters from bean-definition configs. Rejected as premature. If a
  second filter is introduced in the future (e.g. a request-logging
  filter), extracting `common/filter/` at that point is a trivial
  refactor. One class does not justify a new package.
- **Rename to `JwtFilter`**: Short and common in tutorials, but loses
  the `Authentication` context that describes what the filter does to
  the `SecurityContext`. Rejected in favour of the more descriptive
  name.

## Related Decisions

- [ADR-013](ADR-013-feature-based-package-structure.md): Feature-Based Package Structure — establishes that
  cross-cutting concerns belong in `common/`, not in feature packages
- [ADR-014](ADR-014-jwt-library-selection.md): JWT Library Selection — establishes the
  `spring-security-oauth2-jose` approach that makes `JwtDecoder`
  responsible for validation, leaving the filter with claims extraction
  only