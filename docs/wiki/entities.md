# Entity — Why It Exists

## The Core Idea

An entity is a Java class that represents a row in a database table.

Each field maps to a column. Each instance of the class maps to one row.
JPA (Java Persistence API) reads that row from the database and hands you
a populated Java object. You work with it in Java. JPA writes it back.

---

## The Problem: Without Entities

Without entities, every data access requires raw SQL and manual
extraction:

```java
String sql = "SELECT id, email, password, role, status, " +
             "failed_login_attempts, created_at " +
             "FROM users WHERE id = ?";

PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, userId);
ResultSet rs = stmt.executeQuery();

String id                 = rs.getString("id");
String email              = rs.getString("email");
String password           = rs.getString("password");
String role               = rs.getString("role");
String status             = rs.getString("status");
int failedLoginAttempts   = rs.getInt("failed_login_attempts");
LocalDateTime createdAt   = rs.getTimestamp("created_at").toLocalDateTime();
```

Every query. Every service. Every time you need a user, a hotel, a
booking — you write SQL, open a connection, and manually extract every
column by name. One typo in a column name (`"faild_login_attempts"`)
fails silently at runtime, not at compile time. And you repeat this
boilerplate everywhere.

---

## The Solution: Entities + JPA

With an entity, you describe the mapping once:

```java
@Entity
@Table(name = "users")
public class User {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

And now your repository can do:

```java
User user = userRepository.findByEmail("shawon@email.com");
```

JPA runs the SQL, maps every column to the right field, and hands you a
fully populated `User` object. No manual extraction. No repeated
boilerplate. If you rename a field and forget to update a query, the
compiler tells you immediately.

---

## What the Annotations Mean

**`@Entity`**
Tells JPA "this class maps to a database table." Without this, JPA
ignores the class entirely.

**`@Table(name = "users")`**
Specifies which table this entity maps to. Without it, JPA assumes the
table name matches the class name (`User` → `user`). Use it explicitly
to avoid surprises.

**`@Id`**
Marks the primary key field. Every entity must have exactly one.

**`@Column`**
Maps a field to a column with optional constraints. Without it, JPA
still maps the field using the field name as the column name — the
annotation is only needed when you want to customise the name, add
`nullable = false`, `unique = true`, or other constraints.

```java
@Column(name = "failed_login_attempts", nullable = false)
private int failedLoginAttempts;
```

**`@Enumerated(EnumType.STRING)`**
Stores enum values as their string name (`"GUEST"`, `"ADMIN"`) rather
than their ordinal position (`0`, `1`). Always use `STRING` — if you
ever reorder the enum values, ordinal-stored data silently maps to the
wrong value.

**`@CreationTimestamp` / `@UpdateTimestamp`**
Automatically sets the field when a row is created or updated — you
never set these manually:

```java
@CreationTimestamp
private LocalDateTime createdAt;

@UpdateTimestamp
private LocalDateTime updatedAt;
```

---

## miniAgoda's Core Entities

Each one maps to a table. Each one owns its slice of the domain.

```java
// User.java
@Entity @Table(name = "users")
public class User {
    private String id;
    private String email;
    private String password;          // BCrypt hash
    private Role role;                // GUEST, ADMIN, HOTEL_MANAGER
    private UserStatus status;        // ACTIVE, INACTIVE, BANNED
    private int failedLoginAttempts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// Hotel.java
@Entity @Table(name = "hotels")
public class Hotel {
    private String id;
    private String name;
    private String cityId;
    private double rating;
    private HotelStatus status;       // PENDING, ACTIVE, INACTIVE
    private LocalDateTime createdAt;
}

// Booking.java
@Entity @Table(name = "bookings")
public class Booking {
    private String id;
    private String userId;
    private String roomTypeId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int guests;
    private BigDecimal totalAmount;
    private BookingStatus status;     // PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
}

// Payment.java
@Entity @Table(name = "payments")
public class Payment {
    private String id;
    private String bookingId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;     // PENDING, REQUIRES_ACTION, COMPLETED, FAILED, GATEWAY_TIMEOUT
    private String gatewayRef;
    private String gatewayChargeId;
    private String authCode;
    private String idempotencyKey;
    private LocalDateTime paidAt;
}
```

---

## The Relationship With DTOs

This is the most important concept to understand. Entities and DTOs
serve opposite ends of the same pipeline — and they must never cross
their boundaries.

### The Full Pipeline

```
CLIENT
  │
  │  JSON body  (e.g. { "email": "...", "password": "..." })
  ▼
CONTROLLER
  │
  │  Request DTO  (e.g. LoginRequest)
  ▼
SERVICE  ◄──── the only layer that sees both
  │
  │  Entity  (e.g. User)
  ▼
REPOSITORY
  │
  │  SQL
  ▼
DATABASE

  ── and back up ──

DATABASE
  │
  │  SQL result
  ▼
REPOSITORY
  │
  │  Entity  (e.g. User)
  ▼
SERVICE  ◄──── converts Entity → Response DTO here
  │
  │  Response DTO  (e.g. UserProfileResponse)
  ▼
CONTROLLER
  │
  │  JSON response  (e.g. { "id": "...", "email": "...", "role": "..." })
  ▼
CLIENT
```

The service layer is the **only** place that sees both. It converts
inward (DTO → Entity) and outward (Entity → DTO). No other layer
crosses the boundary.

---

## Inward: Request DTO → Entity

When data arrives from the client, it travels as a request DTO. The
service converts it into an entity before touching the database.

```java
// RegisterRequest arriving from the controller
public class RegisterRequest {
    private String email;
    private String password;   // plain text — not yet hashed
    private String fullName;
}
```

```java
// AuthService converts it into a User entity
public void register(RegisterRequest request) {

    // Build the entity from the DTO
    User user = new User();
    user.setId(UUID.randomUUID().toString());
    user.setEmail(request.getEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword())); // hash here
    user.setRole(Role.GUEST);
    user.setStatus(UserStatus.INACTIVE);  // pending email verification
    user.setFailedLoginAttempts(0);

    // Persist the entity
    userRepository.save(user);
}
```

**Why the conversion matters inward:**
- The request DTO carries plain-text password — the entity will store
  the hash. The service is where that transformation happens.
- The entity has fields the client never supplies (`status`, `role`,
  `failedLoginAttempts`) — the service fills them in with safe defaults.
- The entity is what JPA understands. The repository only accepts
  entities — it doesn't know what a `RegisterRequest` is.

---

## Outward: Entity → Response DTO

When data leaves the service, it travels as a response DTO. The service
converts the entity before handing it to the controller.

```java
// AuthService converts User entity → TokenResponse DTO after login
public TokenResponse login(LoginRequest request) {

    User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new InvalidCredentialsException());

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw new InvalidCredentialsException();
    }

    // Entity stays here — only the DTO leaves
    return new TokenResponse(
        jwtUtil.generateAccessToken(user.getId(), user.getRole().name()),
        jwtUtil.generateRefreshToken(user.getId())
    );
}
```

```java
// UserService converts User entity → UserProfileResponse DTO
public UserProfileResponse getProfile(String userId) {

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    // Selective mapping — only safe fields leave
    UserProfileResponse response = new UserProfileResponse();
    response.setId(user.getId());
    response.setEmail(user.getEmail());
    response.setRole(user.getRole().name());
    // password, status, failedLoginAttempts — deliberately not mapped

    return response;
}
```

**Why the conversion matters outward:**
- `password` is on the entity — it must never appear in a response.
  If you returned the `User` entity directly, Spring would serialise
  every field including the hash.
- `failedLoginAttempts`, `status`, `updatedAt` — internal fields that
  are none of the client's business.
- The response DTO is a deliberate, conscious decision about what the
  client is allowed to see. The entity is a complete database record —
  not a public API.

---

## What Happens If You Skip the Boundary

### Returning an entity from a controller (outward violation):

```java
// ❌ Never do this
@GetMapping("/me")
public ResponseEntity<User> getProfile() {
    return ResponseEntity.ok(userService.getUser(userId));
}
```

Response:
```json
{
  "id":                  "shawon-uuid",
  "email":               "shawon@email.com",
  "password":            "$2a$10$N9qo8uLOickgx2ZMRZo",
  "role":                "GUEST",
  "status":              "ACTIVE",
  "failedLoginAttempts": 0,
  "createdAt":           "2024-12-18T09:00:00Z",
  "updatedAt":           "2024-12-18T09:00:00Z"
}
```

Hashed password exposed. Internal fields exposed. Every future field
you add to `User` automatically appears in this response.

### Accepting an entity from a controller (inward violation):

```java
// ❌ Never do this
@PostMapping("/register")
public ResponseEntity<?> register(@RequestBody User user) {
    userService.register(user);
}
```

The client can now set any field on the entity — including `role`,
`status`, and `failedLoginAttempts`. A malicious request could send:

```json
{
  "email":  "attacker@evil.com",
  "password": "password123",
  "role":   "ADMIN",
  "status": "ACTIVE"
}
```

And the attacker just gave themselves an admin account. Request DTOs
with explicit fields are the defence — only the fields you declare on
the DTO can be supplied.

---

## The Service Is the Only Bridge

```
        OUTSIDE WORLD
             │
    ┌────────▼────────┐
    │   Request DTO   │  ← only declared fields accepted
    └────────┬────────┘
             │  inward conversion (DTO → Entity)
    ┌────────▼────────┐
    │     SERVICE     │  ← the only layer that sees both
    └────────┬────────┘
             │  outward conversion (Entity → DTO)
    ┌────────▼────────┐
    │  Response DTO   │  ← only safe fields included
    └────────┬────────┘
             │
        OUTSIDE WORLD

    ┌────────────────┐
    │    ENTITY      │  ← stays inside, never crosses either boundary
    └────────────────┘
```

The repository only works with entities — it maps SQL results to
entities and persists entities to SQL. The controller only works with
DTOs — it deserialises request DTOs from JSON and serialises response
DTOs to JSON. The service translates between the two worlds.

---

## Glossary

| Term | Meaning |
|---|---|
| Entity | A Java class that maps to a database table — internal to the service layer |
| JPA | Java Persistence API — the standard for mapping Java objects to database rows |
| `@Entity` | Annotation that tells JPA this class maps to a table |
| `@Id` | Marks the primary key field |
| `@Column` | Customises the column mapping — name, nullability, uniqueness |
| `@Enumerated(STRING)` | Stores enum as its string name, not its ordinal position |
| Request DTO | Carries data inbound from the client — validated at the boundary |
| Response DTO | Carries data outbound to the client — only safe fields included |
| Inward conversion | DTO → Entity, done in the service before persisting |
| Outward conversion | Entity → DTO, done in the service before returning to controller |
| Mass assignment | A vulnerability where a client sets fields they shouldn't — prevented by request DTOs |

---

## One-Paragraph Summary

An entity is a Java class that maps to a database table — each field is
a column, each instance is a row. JPA handles the SQL automatically so
you work with objects instead of raw result sets. The entity is a
complete database record and must never leave the service layer: sending
it directly to the controller exposes internal fields like hashed
passwords and allows clients to set fields they shouldn't. DTOs are the
solution — request DTOs are the gate inward (only declared fields
accepted, validated at the boundary), response DTOs are the gate outward
(only safe fields included, deliberately chosen). The service is the
only layer that sees both: it converts request DTOs into entities before
persisting, and converts entities into response DTOs before returning.
Everything above the service sees DTOs. Everything below the service
sees entities. The two never cross.