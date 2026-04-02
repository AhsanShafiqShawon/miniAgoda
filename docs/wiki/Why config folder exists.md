# The `config/` Package — Why It Exists

## The Core Idea

A Spring Boot application has two kinds of classes:

**Classes that do business logic** — search hotels, create bookings,
charge cards, send notifications. These live in `service/`, `controller/`,
`repository/`, `entity/`.

**Classes that wire the application together** — decide which endpoints
are secured, how tokens are structured, which beans exist, what encoding
algorithm to use. These live in `config/`.

The config classes don't *do* miniAgoda's work. They decide *how miniAgoda
is assembled* before the work begins.

---

## The Question Each Layer Answers

| Package | Question it answers |
|---|---|
| `entity/` | What does our data look like? |
| `repository/` | How do we read and write that data? |
| `service/` | What does our application do? |
| `controller/` | How does the outside world call us? |
| `config/` | How is the application put together? |

If you deleted every file in `config/`, miniAgoda's business logic would
be completely unchanged — but the application would either lock every
endpoint, fail to start, or behave in unexpected ways. The config layer
is infrastructure, not domain.

---

## What Lives in `config/`

### `SecurityConfig.java`

**What it does:** Tells Spring Security which endpoints are public and
which require a valid JWT.

Without this file, Spring Security locks down every endpoint by default.
Nothing works. With it, you define the rules:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()   // login, register — no token needed
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()                      // everything else needs a JWT
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

It also plugs your custom `JwtFilter` into the filter chain so it runs
before every request.

**Why it's not in `service/`:** It's not business logic. It's a
declaration of the application's security posture. Changing it doesn't
change what miniAgoda does — it changes who is allowed to do it.

---

### `JwtConfig.java`

**What it does:** Reads JWT settings from `application.properties` and
makes them injectable anywhere in the application.

```java
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    private String secretKey;
    private long accessTokenExpiry;   // e.g. 86400000 (24 hours in ms)
    private long refreshTokenExpiry;  // e.g. 604800000 (7 days in ms)

    // getters and setters
}
```

```properties
# application.properties
jwt.secret-key=your-256-bit-secret
jwt.access-token-expiry=86400000
jwt.refresh-token-expiry=604800000
```

Any class that needs the secret key or expiry duration injects
`JwtConfig` — it doesn't hardcode these values.

**Why it's not in `JwtUtil.java`:** `JwtUtil` uses the values. `JwtConfig`
owns the values. Separating them means you can change the expiry duration
in one place — `application.properties` — without touching any logic.

---

### `AppConfig.java`

**What it does:** A home for beans that don't logically belong to any
specific domain package. The most common residents:

**`BCryptPasswordEncoder`** — the bean Spring uses to hash and verify
passwords. Defined once here, injected wherever needed:

```java
@Configuration
public class AppConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}
```

Without this, Spring doesn't know which password encoder to use when
`AuthService` asks for one. It's not a business decision — it's a
wiring decision.

**CORS configuration** also often lives here — deciding which origins
(domains) are allowed to call your API from a browser. This is
infrastructure, not domain logic.

**Why it's not in `AuthService.java`:** `AuthService` shouldn't decide
what hashing algorithm the whole application uses. It should just ask
for a `PasswordEncoder` and use whatever it gets. `AppConfig` is where
that decision is made once, centrally.

---

## The Pattern: Separation of Concerns

The reason `config/` exists is the same reason all the other packages
exist — **separation of concerns**. Each class should have one reason to
change.

If `AuthService` also contained the security rules, you'd have to open
a business logic class every time you wanted to change which endpoints
are public. That's the wrong place.

If `JwtUtil` also contained the secret key and expiry values, you'd have
to open a utility class every time the operations team wanted to rotate
the secret. That's also the wrong place.

`config/` collects all the "how is this assembled" decisions into one
predictable location. A new developer joining the team knows exactly
where to look when the question is about security rules, token settings,
or application-level beans.

---

## What Changes When You Change Each Config File

| File | What changes | What stays the same |
|---|---|---|
| `SecurityConfig.java` | Which endpoints require auth, which roles can access what | All business logic, all data |
| `JwtConfig.java` | Token expiry duration, secret key | How tokens are generated and verified |
| `AppConfig.java` | Password hashing algorithm, CORS origins, mapper settings | All service and domain logic |

---

## Full `config/` in Context

```
src/main/java/com/miniagoda/
├── common/
│   ├── config/
│   │   ├── SecurityConfig.java    ← who can call what
│   │   ├── JwtConfig.java         ← token settings from application.properties
│   │   └── AppConfig.java         ← BCrypt, ModelMapper, CORS, misc beans
│   ├── exception/
│   ├── response/
│   └── util/
│       └── JwtUtil.java           ← uses JwtConfig, does not own it
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java           ← uses BCryptPasswordEncoder from AppConfig
│   └── ...
└── ...
```

---

## One-Paragraph Summary

`config/` exists because some classes in Spring don't perform business
logic — they assemble the application. `SecurityConfig` declares which
endpoints are public and which require a JWT, and installs the JWT filter
into the request pipeline. `JwtConfig` reads token settings from
`application.properties` and makes them injectable, so expiry durations
and secret keys are defined in one place and never hardcoded. `AppConfig`
defines beans like `BCryptPasswordEncoder` that are infrastructure
decisions rather than domain decisions. Together these three files answer
the question "how is miniAgoda put together?" — a question that belongs
in one predictable place, separate from the question "what does miniAgoda
do?"