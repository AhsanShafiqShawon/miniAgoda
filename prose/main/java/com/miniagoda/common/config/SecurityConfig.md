# SecurityConfig — Code Prose

`com.miniagoda.common.config.SecurityConfig`

---

## `securityFilterChain(HttpSecurity http)`

This method is where the application's HTTP security rules are defined. Spring Security calls it once at startup, and whatever `SecurityFilterChain` it returns becomes the gatekeeper for every incoming HTTP request.

It receives an `HttpSecurity` builder — think of it as a configuration surface where you declare how the application should behave around sessions, CSRF, and access control.

The first thing it does is disable CSRF protection entirely. This is intentional for a stateless REST API: CSRF attacks exploit session cookies, and since this application won't be using session cookies, the protection is unnecessary overhead.

Next it sets the session creation policy to `STATELESS`. This tells Spring Security never to create or consult an `HttpSession` for any request. Every request must be self-contained — authenticated by whatever is on the wire, not by a server-side session. This is the standard posture for a JWT-based API.

Then it configures request authorization. Right now, every request is permitted without any authentication check — `anyRequest().permitAll()`. This is a temporary placeholder. The TODO comments spell out the intended end state clearly: public routes for auth and search, role-gated routes for hosts and admins, and everything else requiring a valid authenticated user.

The reason it's open right now is that the authorization rules depend on `JwtAuthFilter` — a filter that will intercept each request, extract the JWT from the `Authorization` header, validate it, and load the user into the security context. That filter doesn't exist yet. Until it does, locking down routes would lock out everyone, including developers, so `permitAll()` acts as a safe default during early development.

The commented-out block at the bottom shows exactly where `JwtAuthFilter` will be inserted once it's ready: just before `UsernamePasswordAuthenticationFilter` in the filter chain, which is the standard position for custom token-based authentication filters in Spring Security.

Finally, `http.build()` assembles everything into a `SecurityFilterChain` and returns it to Spring, which registers it as the active security policy for the application.