# Layered Architecture

## The Big Picture

Every feature in miniAgoda follows the same structure:

```
Controller → Service → Repository → Database
```

This is called **Layered Architecture**. Each layer has exactly one job.
No layer does the job of another. No layer skips past its neighbour to
talk to a layer further away.

---

## The Layers

```
┌─────────────────────────────────────────────────┐
│                   CLIENT                        │
│         (Browser, Mobile App, API caller)       │
└─────────────────┬───────────────────────────────┘
                  │  HTTP request / response
┌─────────────────▼───────────────────────────────┐
│               CONTROLLER                        │
│   HTTP in, HTTP out. No business logic.         │
│   Speaks: Request DTO, Response DTO             │
└─────────────────┬───────────────────────────────┘
                  │  method call / return value
┌─────────────────▼───────────────────────────────┐
│                SERVICE                          │
│   The brain. All business logic lives here.     │
│   Converts DTO ↔ Entity.                        │
│   Speaks: DTO (toward controller)               │
│           Entity (toward repository)            │
└─────────────────┬───────────────────────────────┘
                  │  method call / return value
┌─────────────────▼───────────────────────────────┐
│              REPOSITORY                         │
│   Database in, database out. No logic.          │
│   Speaks: Entity                                │
└─────────────────┬───────────────────────────────┘
                  │  SQL
┌─────────────────▼───────────────────────────────┐
│               DATABASE                          │
│   Stores and retrieves rows.                    │
│   Speaks: Tables, columns, rows                 │
└─────────────────────────────────────────────────┘
```

---

## What Each Layer Is Responsible For

### Controller
**One job: translate HTTP into a service call, and a service result
into HTTP.**

- Receives an HTTP request
- Deserialises the body into a request DTO
- Calls the service
- Wraps the result in `ApiResponse`
- Returns an HTTP response with the right status code

What it knows about: HTTP methods, status codes, request bodies, URL
parameters, response headers.

What it knows nothing about: SQL, database tables, business rules,
password hashing, token generation.

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request) {

        TokenResponse token = authService.login(request); // just calls the service
        return ResponseEntity.ok(ApiResponse.ok(token, "Login successful"));
    }
}
```

The controller has no idea how login works. It just passes the request
to the service and wraps the result.

---

### Service
**One job: contain all business logic and coordinate the layers around
it.**

- Validates business rules ("is this booking still PENDING_PAYMENT?")
- Makes decisions ("does this promo code apply?")
- Coordinates multiple repositories when needed
- Converts inbound request DTOs into entities
- Converts outbound entities into response DTOs
- The only layer that sees both DTOs and entities

What it knows about: business rules, domain concepts, entities, DTOs,
other services it needs to call.

What it knows nothing about: HTTP status codes, request headers, SQL
syntax, database columns.

```java
@Service
public class AuthService {

    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final JwtUtil             jwtUtil;

    public TokenResponse login(LoginRequest request) {

        // Business rule: user must exist
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException());

        // Business rule: password must match
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // Business rule: account must be active
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        // Convert entity → DTO before returning to controller
        return new TokenResponse(
            jwtUtil.generateAccessToken(user.getId(), user.getRole().name()),
            jwtUtil.generateRefreshToken(user.getId())
        );
    }
}
```

The service has no idea it was called over HTTP. It just receives a
`LoginRequest`, applies rules, and returns a `TokenResponse`.

---

### Repository
**One job: talk to the database.**

- Runs queries
- Returns entities
- Persists entities
- No business logic whatsoever

What it knows about: the entity it manages, the table it queries.

What it knows nothing about: HTTP, business rules, other repositories,
DTOs.

```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

Spring Data JPA generates the SQL automatically from the method names.
`findByEmail` becomes `SELECT * FROM users WHERE email = ?`. No SQL
written by hand for basic operations.

---

### DTO
**One job: carry data across the controller boundary.**

- Request DTOs carry data inbound — validated at the boundary
- Response DTOs carry data outbound — only safe fields included
- No business logic
- Never touches the database

See the [DTO wiki page](dto-data-transfer-object.md) for full detail.

---

### Entity
**One job: represent a database row.**

- Maps fields to columns
- JPA reads and writes it automatically
- No business logic
- Never crosses the service boundary outward to the controller

See the [Entity wiki page](entities.md) for full detail.

---

## The Strict Boundary Rules

```
Controller   never touches   Entity
Repository   never touches   DTO
Service      touches both — and is the only layer that converts between them
```

And the communication rule:

```
Each layer only talks to the layer directly next to it.

Controller → Service     ✅
Service    → Repository  ✅
Controller → Repository  ❌  skipping a layer
Repository → Service     ❌  going backwards
Controller → Database    ❌  skipping two layers
```

---

## A Complete Request — Login

Watching all layers work together end to end:

```
1. Browser sends:
   POST /api/v1/auth/login
   { "email": "shawon@email.com", "password": "hunter2" }

2. AuthController receives the HTTP request
   Deserialises body → LoginRequest DTO
   Calls authService.login(request)

3. AuthService receives LoginRequest
   Calls userRepository.findByEmail("shawon@email.com")
   Verifies password with BCrypt
   Checks account is ACTIVE
   Converts User entity → TokenResponse DTO
   Returns TokenResponse to controller

4. UserRepository receives findByEmail call
   Runs: SELECT * FROM users WHERE email = 'shawon@email.com'
   Maps result row → User entity
   Returns User entity to service

5. AuthController receives TokenResponse from service
   Wraps in ApiResponse
   Returns HTTP 200 with JSON body

6. Browser receives:
   { "success": true, "data": { "accessToken": "...", "refreshToken": "..." } }
```

Each layer did exactly one thing. None of them knew what the others
were doing internally.

---

## Why This Structure Exists

### Reason 1 — Each layer can change independently

If you switch from PostgreSQL to MongoDB, you only change the
repository layer. The service and controller are untouched — they never
wrote SQL.

If you switch from REST to GraphQL, you only change the controller
layer. The service and repository are untouched — they never dealt
with HTTP.

### Reason 2 — Each layer is testable in isolation

You can test the service by mocking the repository — no real database
needed. You can test the controller by mocking the service — no
business logic needed. Each layer has a clean seam where you can cut
and replace with a mock.

### Reason 3 — The codebase is predictable

A new developer joins the team. They need to find where the "booking
expiry" rule lives. They don't have to search the whole codebase.
They know it's in `BookingService` — because all business rules live
in services. Always.

### Reason 4 — Security by design

Request DTOs prevent mass assignment attacks (clients setting fields
they shouldn't). Response DTOs prevent data leaks (passwords and
internal fields never reach the controller). The entity stays internal.

---

## How the Supporting Packages Fit In

The layered structure handles features. `common/` handles the
cross-cutting infrastructure that all features share.

```
src/main/java/com/miniagoda/
│
├── common/                                 ← shared infrastructure, no business logic
│   ├── config/                             ← wires the application together
│   │   ├── SecurityConfig.java             ← which endpoints need auth
│   │   ├── JwtConfig.java                  ← token settings
│   │   └── AppConfig.java                  ← BCrypt, ModelMapper, CORS
│   ├── exception/
│   │   └── GlobalExceptionHandler.java     ← converts exceptions → ErrorResponse
│   ├── response/
│   │   ├── ApiResponse.java                ← wraps all success responses
│   │   └── ErrorResponse.java              ← wraps all error responses
│   └── util/
│       └── JwtUtil.java                    ← stateless JWT helpers
│
├── auth/                                   ← one feature, all four layers
│   ├── AuthController.java                 ← layer 1: HTTP
│   ├── AuthService.java                    ← layer 2: business logic
│   ├── dto/                                ← boundary objects
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   └── TokenResponse.java
│   └── entity/
│       └── RefreshToken.java               ← layer 4: database row
│
├── user/
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java                 ← layer 3: database access
│   ├── dto/
│   └── entity/
│       └── User.java
│
├── hotel/
│   ├── HotelSearchController.java
│   ├── HotelSearchService.java
│   ├── HotelRepository.java
│   ├── dto/
│   └── entity/
│       └── Hotel.java
│
└── ... (same pattern for booking, payment, review, etc.)
```

---

## One-Line Summary of Each Piece

| Piece | One line |
|---|---|
| Controller | Translates HTTP ↔ service calls |
| Service | Contains all business logic and converts DTO ↔ Entity |
| Repository | Runs database queries and returns entities |
| DTO | Carries data across the controller boundary — never touches the DB |
| Entity | Represents a database row — never crosses the service boundary outward |
| `common/config/` | Wires the application together — security rules, token settings, shared beans |
| `common/response/` | Defines the envelope every response travels in |
| `common/util/` | Stateless helpers used by more than one feature |
| `common/exception/` | Catches all exceptions and converts them to `ErrorResponse` |

---

## The Pages in This Wiki

Each concept above has its own dedicated page:

- [DTO](dto-data-transfer-object.md) — what it is, inward and outward, the boundary rules in detail
- [Entity](entities.md) — what it is, annotations, inward/outward relationship with DTOs
- [config/ package](why-config-folder-exists.md) — SecurityConfig, JwtConfig, AppConfig
- [response/ package](why-response-folder-exists.md) — ApiResponse, ErrorResponse, the envelope pattern
- [util/ package](why-util-folder-exists.md) — JwtUtil, the three conditions for util
- [JWT](jwt-json-web-token.md) — what a JWT is, access tokens, refresh tokens, the two-token flow
- [Cookies](cookies.md) — what cookies are, HttpOnly, Secure, SameSite, XSS, CSRF

---

## One-Paragraph Summary

Layered Architecture organises every feature into four layers: Controller
(HTTP in and out), Service (all business logic), Repository (database in
and out), and Database. Each layer has one job and talks only to the
layer directly next to it — no skipping. DTOs live at the controller
boundary and carry data in and out of the application. Entities live at
the repository boundary and represent database rows. The service is the
only layer that sees both, and the only layer that converts between them.
This structure means each layer can change independently, each layer can
be tested in isolation, the codebase is predictable (business rules are
always in services), and security is enforced by design (passwords never
reach controllers, clients cannot set fields they shouldn't).