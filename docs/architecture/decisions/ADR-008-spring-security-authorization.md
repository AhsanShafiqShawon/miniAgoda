# ADR-008: Spring Security for Role-Based Authorization

## Status
Accepted

## Context

Role-based access control needs to be enforced throughout miniAgoda —
only `HOTEL_ADMIN` can manage hotels, only `SUPER_ADMIN` can ban users,
only `GUEST` can write reviews, and so on.

Two options were considered:

1. **Explicit `authorizeUserRole()` service method** — a method in
   `AuthService` that checks a user's role and throws
   `UnauthorizedException` if they don't have the required role.
   Called manually at the start of every service method.

2. **Spring Security annotations** — role checks declared at the
   controller or service layer using `@PreAuthorize`, `@RolesAllowed`,
   or `@Secured` annotations. Enforced automatically by Spring Security's
   AOP proxy before the method executes.

## Decision

Use Spring Security annotations for role-based authorization.
No explicit `authorizeUserRole()` service method in `AuthService`.

```java
// Controller layer — declarative, clean
@PreAuthorize("hasRole('HOTEL_ADMIN')")
@PostMapping("/hotels")
public ResponseEntity<Hotel> addHotel(@RequestBody AddHotelRequest request) {
    return ResponseEntity.ok(hotelService.addHotel(request));
}

@PreAuthorize("hasRole('SUPER_ADMIN')")
@DeleteMapping("/users/{id}")
public ResponseEntity<Void> banUser(@PathVariable UUID id) {
    adminService.banUser(id);
    return ResponseEntity.noContent().build();
}
```

JWT tokens carry the user's role as a claim. Spring Security validates
the token and populates the `SecurityContext` on every request.
`@PreAuthorize` expressions are evaluated against the `SecurityContext`.

## Consequences

**Positive:**
- Authorization logic is declarative — visible at the method signature
- No manual role checks scattered across service methods
- Spring Security handles token validation, SecurityContext population,
  and method security automatically
- Industry standard for Spring Boot applications
- Easier to audit — all access control rules visible at controller level
- No `authorizeUserRole()` method needed in `AuthService`

**Negative:**
- Requires Spring Security configuration (SecurityFilterChain, JWT filter)
- `@PreAuthorize` expressions are strings — not type-safe
- Testing requires mocking the SecurityContext

## Spring Security Configuration

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

## Alternatives Considered

- **Explicit `authorizeUserRole()` in `AuthService`**: More verbose —
  every service method needs a manual role check. Rejected in favour
  of declarative Spring Security annotations.