# DTO — Data Transfer Object

## The Problem: Exposing Your Internals

Your `User.java` entity maps directly to the `users` table in the
database. It holds everything:

```java
public class User {
    private String        id;
    private String        email;
    private String        password;      // BCrypt hash
    private String        role;
    private String        status;        // ACTIVE, INACTIVE, BANNED
    private int           failedLoginAttempts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

Now Shawon calls `GET /api/v1/users/me` to see her profile. If you
return the `User` entity directly, the response looks like:

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

You just sent Shawon her own hashed password. You also sent `status`,
`failedLoginAttempts`, and `updatedAt` — internal fields she has no
business seeing.

Worse: every time you add a field to the `User` entity for internal
purposes, it automatically appears in the API response whether you meant
it to or not. Your database schema has become your API contract.

---

## The Solution: A Purpose-Built Shape for Each Direction

A **DTO — Data Transfer Object** is a plain object whose only job is to
carry data between layers. It has no business logic, no database
annotations, no behaviour. Just fields.

Instead of returning `User`, you return `UserProfileResponse`:

```java
public class UserProfileResponse {
    private String id;
    private String email;
    private String role;
}
```

Response:
```json
{
  "id":    "shawon-uuid",
  "email": "shawon@email.com",
  "role":  "GUEST"
}
```

Password gone. Internal fields gone. You control exactly what leaves
the system — field by field, endpoint by endpoint.

---

## DTOs Go Both Ways

### Incoming — Request DTOs

What the client sends in. The controller receives these from the HTTP
request body.

```java
// LoginRequest.java
public class LoginRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
```

```java
// RegisterRequest.java
public class RegisterRequest {
    @Email
    @NotBlank
    private String email;

    @Size(min = 8)
    @NotBlank
    private String password;

    @NotBlank
    private String fullName;
}
```

The entity never touches the raw HTTP body directly. The request DTO
is the gatekeeper — it validates what comes in before anything reaches
the service layer.

### Outgoing — Response DTOs

What the server sends back. The controller wraps these in `ApiResponse`
before returning.

```java
// TokenResponse.java
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
}
```

```java
// UserProfileResponse.java
public class UserProfileResponse {
    private String id;
    private String email;
    private String role;
}
```

```java
// HotelSummaryResponse.java
public class HotelSummaryResponse {
    private String hotelId;
    private String name;
    private double rating;
    private double startingFromPrice;
}
```

---

## The Full Flow — Each Layer Gets Its Own Shape

```
Browser  →  LoginRequest  →  AuthService  →  User (entity)  →  DB
                                          ↓
Browser  ←  TokenResponse ←  AuthService
```

| Shape | Direction | Purpose |
|---|---|---|
| `LoginRequest` | Inbound | Carries what the user typed — email + password |
| `User` | Internal | Maps to the database row — never leaves the service layer |
| `TokenResponse` | Outbound | Carries only what the client needs after login |

The entity lives and dies inside the service layer. It never reaches
the controller as a return value, and it never comes in from the HTTP
body as a raw parameter. DTOs are the gates on both sides.

---

## Three Concrete Benefits

### 1. Security

You decide field by field what is exposed. Passwords, internal status
flags, audit timestamps, failed login counters — they stay invisible to
the outside world because they simply don't exist on the response DTO.

### 2. Stability

You can refactor your database schema freely without breaking your API
contract.

Rename a column in `User`? Update the entity and the mapping in the
service. The DTO — and therefore the API response — stays identical.
Frontend developers notice nothing. Their code keeps working.

Without DTOs, renaming a database column immediately changes the JSON
field name in the response, breaking every client that depends on it.

### 3. Validation

Request DTOs are where input validation lives — not entities:

```java
public class RegisterRequest {
    @Email(message = "Must be a valid email address")
    @NotBlank(message = "Email is required")
    private String email;

    @Size(min = 8, message = "Password must be at least 8 characters")
    @NotBlank(message = "Password is required")
    private String password;
}
```

Validation belongs at the boundary — the moment data enters your system.
Entities shouldn't carry validation annotations because they represent
already-trusted data that came from your own database. By the time data
reaches an entity, it has already been validated.

---

## Mapping: Entity ↔ DTO

The service layer is responsible for converting between entities and
DTOs. This can be done manually or with a library like `ModelMapper`
(configured in `AppConfig`).

**Manual mapping:**
```java
// Inside AuthService
public TokenResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail());
    // verify password...

    // Convert to DTO — entity never leaves the service
    return new TokenResponse(
        jwtUtil.generateAccessToken(user.getId(), user.getRole()),
        jwtUtil.generateRefreshToken(user.getId())
    );
}
```

**With ModelMapper:**
```java
public UserProfileResponse getProfile(String userId) {
    User user = userRepository.findById(userId);
    return modelMapper.map(user, UserProfileResponse.class);
}
```

The controller never does the mapping. The entity never reaches the
controller. The service is the only layer that sees both.

---

## What miniAgoda's DTOs Look Like by Feature

```
auth/dto/
├── LoginRequest.java          ← email + password
├── RegisterRequest.java       ← email + password + fullName
├── TokenResponse.java         ← accessToken + refreshToken
└── RefreshTokenRequest.java   ← refreshToken

user/dto/
├── UserProfileResponse.java   ← id + email + role
└── UpdateProfileRequest.java  ← fullName + phone

hotel/dto/
├── HotelSummaryResponse.java  ← id + name + rating + startingFromPrice
└── HotelDetailResponse.java   ← full hotel info + room types

booking/dto/
├── CreateBookingRequest.java  ← hotelId + roomTypeId + checkIn + checkOut + guests
└── BookingResponse.java       ← bookingId + status + totalAmount + dates

payment/dto/
├── PaymentRequest.java        ← bookingId + paymentMethodId + amount + currency
└── PaymentResponse.java       ← paymentId + status + paidAt + last4
```

Each feature owns its own DTOs. They live inside the feature package,
not in `common/` — because a `LoginRequest` only matters to `auth/`,
a `PaymentRequest` only matters to `payment/`.

---

## Full Picture in Context

```
src/main/java/com/miniagoda/
├── auth/
│   ├── AuthController.java        ← receives LoginRequest, returns ApiResponse<TokenResponse>
│   ├── AuthService.java           ← maps LoginRequest → User → TokenResponse
│   ├── dto/
│   │   ├── LoginRequest.java      ← inbound
│   │   ├── RegisterRequest.java   ← inbound
│   │   └── TokenResponse.java     ← outbound
│   └── entity/
│       └── RefreshToken.java      ← internal — never exposed directly
├── user/
│   ├── UserController.java        ← returns ApiResponse<UserProfileResponse>
│   ├── UserService.java           ← maps User → UserProfileResponse
│   ├── dto/
│   │   └── UserProfileResponse.java
│   └── entity/
│       └── User.java              ← internal — never exposed directly
└── ...
```

---

## Glossary

| Term | Meaning |
|---|---|
| DTO | Data Transfer Object — a plain object that carries data between layers |
| Request DTO | A DTO that carries data coming in from the client |
| Response DTO | A DTO that carries data going out to the client |
| Entity | A class that maps to a database table — internal, never exposed directly |
| Mapping | Converting an entity to a DTO or a DTO to an entity |
| `ModelMapper` | A library that automates entity ↔ DTO conversion |
| Validation | Rules applied to request DTOs at the boundary — `@Email`, `@NotBlank`, `@Size` |
| API contract | The agreed shape of requests and responses between server and client |

---

## One-Paragraph Summary

A DTO is a plain object whose only job is to carry data between layers —
specifically between the outside world and your application. Without
DTOs, controllers return entities directly, which exposes database fields
like hashed passwords and internal flags, and makes your database schema
your API contract. With DTOs, each direction gets its own purpose-built
shape: request DTOs carry and validate what comes in, response DTOs
carry exactly and only what the client needs. The entity lives and dies
inside the service layer — it never reaches the controller as a return
value, and never comes in raw from the HTTP body. This gives you three
things: security (sensitive fields are simply absent from the DTO),
stability (database changes don't break the API), and clean validation
(input is checked at the boundary before it touches any business logic).