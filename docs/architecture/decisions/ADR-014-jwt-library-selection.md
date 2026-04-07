# ADR-014: JWT Library Selection

## Status
Accepted

## Context

miniAgoda requires JWT-based authentication for its stateless API. Two
responsibilities need to be covered:

- **Token generation** — signing and building JWTs when a user logs in
- **Token validation** — verifying incoming JWTs on protected endpoints

Three libraries were evaluated:

**`jjwt` (io.jsonwebtoken)** is the most widely used JWT library in the
Spring Boot ecosystem. It provides a fluent API for both generating and
validating tokens and is straightforward to set up for simple username/password
auth flows.

**`nimbus-jose-jwt` (com.nimbusds)** is the library Spring Security uses
internally. It is lower-level and more verbose but is the foundation for
Spring's OAuth2 and OpenID Connect support.

**`spring-security-oauth2-jose`** wraps Nimbus behind Spring's abstractions.
It provides `JwtDecoder` and `JwtEncoder` beans that integrate directly into
the Security filter chain, and it is the standard library for OAuth2 resource
servers and OAuth2 clients (e.g. Google Sign-In) in Spring Boot projects.

The decision was made in the context of a known near-future requirement:
**Sign in with Google**. Google Sign-In uses OAuth2 + OpenID Connect, which
requires validating Google's ID tokens — JWTs signed with Google's RSA keys,
fetched from a public JWKS endpoint.

## Decision

Use **`spring-security-oauth2-jose`** via two starters:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

Token responsibilities are split as follows:

- **Token generation** (`JwtUtil`) — uses Spring's `JwtEncoder` to sign and
  build access and refresh tokens
- **Token validation** — delegated entirely to Spring Security's built-in
  `JwtDecoder` bean, wired into the filter chain via `SecurityConfig`

`JwtUtil` is therefore generation-only. It does not validate tokens.
Validation is handled automatically by the framework.

## Consequences

**Positive:**
- Single JWT library covers both miniAgoda's own tokens and future Google
  ID tokens — no need to maintain two libraries side by side
- Token validation requires no hand-written filter logic — Spring Security's
  `JwtDecoder` handles it automatically once configured in `SecurityConfig`
- Adding Google Sign-In in a future phase requires only adding OAuth2 client
  configuration, not a new library or a new validation path
- Aligns with Spring Security's recommended approach for stateless APIs

**Negative:**
- The API is more verbose than `jjwt` — `JwtEncoder` and `JwtClaimsSet` are
  more ceremonious than jjwt's fluent builder
- Less beginner-friendly — most tutorials use `jjwt`, so external examples
  will not map directly to this codebase
- `JwtUtil` is generation-only, which is a non-obvious split for developers
  accustomed to a single utility class owning both responsibilities

## Alternatives Considered

- **`jjwt`**: Simpler API and better tutorial coverage, but does not support
  OAuth2 or OpenID Connect natively. Adding Google Sign-In later would require
  introducing a second JWT library. Rejected to avoid that future complexity.
- **`nimbus-jose-jwt` directly**: Equivalent capability to the chosen approach
  but without Spring's abstractions. More boilerplate for no benefit given that
  Spring Boot already wraps it cleanly. Rejected in favour of the higher-level
  starter.