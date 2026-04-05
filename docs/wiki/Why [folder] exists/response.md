# The `common/response/` Package — Why It Exists

## The Problem: Every Controller Invents Its Own Shape

Without a shared response structure, every controller returns whatever
feels natural to the developer who wrote it.

**`HotelSearchController` returns:**
```json
{
  "hotels": [...],
  "page": 0,
  "totalResults": 2
}
```

**`AuthController` returns:**
```json
{
  "token": "eyJhbG...",
  "userId": "shawon-uuid"
}
```

**`BookingController` returns on error:**
```json
{
  "message": "Room not available",
  "timestamp": "2024-12-18T09:48:33Z"
}
```

**`PaymentController` returns on error:**
```json
{
  "error": "CARD_DECLINED",
  "reason": "Insufficient funds"
}
```

A frontend developer consuming this API now has to handle a different
structure for every endpoint. Success responses have different field
names. Error responses have different shapes. Some include a timestamp,
some don't. Some use `"message"`, some use `"reason"`.

With 17 services worth of endpoints, this becomes unmaintainable.

---

## The Solution: One Envelope for Everything

`common/response/` defines a single wrapper that every response travels
in — regardless of which controller sent it or whether it succeeded or
failed.

The frontend learns one contract once, and it works everywhere.

---

## `ApiResponse<T>.java`

Wraps every **successful** response:

```java
public class ApiResponse<T> {
    private boolean success;
    private String  message;
    private T       data;

    // static factory methods
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }
}
```

The `<T>` is a Java generic — `data` can hold anything. A search result,
a token, a booking summary, a user profile. The outer shape is always
identical.

**Hotel search response:**
```json
{
  "success": true,
  "message": "Hotels retrieved successfully",
  "data": {
    "hotels": [...],
    "page": 0,
    "totalResults": 2
  }
}
```

**Login response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "dGhpcy...",
    "userId": "shawon-uuid"
  }
}
```

**Booking confirmation response:**
```json
{
  "success": true,
  "message": "Booking confirmed",
  "data": {
    "bookingId": "booking-001-uuid",
    "status": "CONFIRMED",
    "checkIn": "2024-12-20",
    "checkOut": "2024-12-25"
  }
}
```

The `data` field changes. Everything around it stays the same.

---

## `ErrorResponse.java`

Wraps every **failed** response:

```java
public class ErrorResponse {
    private boolean success;
    private String  error;
    private String  message;
    private String  timestamp;
}
```

**Room no longer available:**
```json
{
  "success": false,
  "error":   "ROOM_NOT_AVAILABLE",
  "message": "The room you selected is no longer available.",
  "timestamp": "2024-12-18T09:48:33Z"
}
```

**Card declined:**
```json
{
  "success": false,
  "error":   "CARD_DECLINED",
  "message": "Insufficient funds.",
  "timestamp": "2024-12-18T09:48:33Z"
}
```

**Booking expired:**
```json
{
  "success": false,
  "error":   "BOOKING_EXPIRED",
  "message": "Your booking hold expired at 09:55 UTC. Please search again.",
  "timestamp": "2024-12-18T09:55:01Z"
}
```

Same shape every time. The frontend checks `success`. If `false`, it
reads `error` (for programmatic handling) and `message` (to show the
user). It never has to guess what field the error lives in.

---

## How Controllers Use It

Without the wrapper, a controller looks like this:

```java
// ❌ Before — every controller invents its own shape
@GetMapping("/search")
public SearchResult search(...) {
    return hotelSearchService.search(query);
}
```

With the wrapper:

```java
// ✅ After — consistent envelope on every endpoint
@GetMapping("/search")
public ResponseEntity<ApiResponse<SearchResult>> search(...) {
    SearchResult result = hotelSearchService.search(query);
    return ResponseEntity.ok(ApiResponse.ok(result, "Hotels retrieved successfully"));
}
```

The service layer (`HotelSearchService`) still returns its own domain
object (`SearchResult`). The controller wraps it in `ApiResponse` before
sending it out. The domain stays clean — it doesn't know about the
response envelope.

---

## The Frontend Contract

Once `ApiResponse` and `ErrorResponse` exist, the frontend can write one
handler for all API calls:

```javascript
async function callApi(url, options) {
    const response = await fetch(url, options);
    const body = await response.json();

    if (body.success) {
        return body.data;       // always here on success
    } else {
        throw new Error(body.message);  // always here on failure
    }
}
```

One function. Works for search, login, booking, payment, reviews —
every endpoint in the system.

---

## Why It Lives in `common/`

Every controller in the application uses `ApiResponse` and
`ErrorResponse` — `AuthController`, `HotelSearchController`,
`PaymentController`, `BookingController`, all 17 services worth of
controllers.

It belongs to no single domain. It's not an auth concept, not a booking
concept, not a payment concept. It's a cross-cutting concern — shared
infrastructure that every part of the application depends on.

`common/` is the home for exactly this kind of code: things that are
used everywhere and owned by no one feature in particular.

---

## Full `common/response/` in Context

```
src/main/java/com/miniagoda/
├── common/
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── JwtConfig.java
│   │   └── AppConfig.java
│   ├── exception/
│   │   └── GlobalExceptionHandler.java   ← catches exceptions, builds ErrorResponse
│   ├── response/
│   │   ├── ApiResponse.java              ← wraps all success responses
│   │   └── ErrorResponse.java            ← wraps all error responses
│   └── util/
│       └── JwtUtil.java
├── auth/
│   └── AuthController.java               ← returns ApiResponse<TokenResponse>
├── user/
│   └── UserController.java               ← returns ApiResponse<UserDto>
├── hotel/
│   └── HotelSearchController.java        ← returns ApiResponse<SearchResult>
└── ...
```

Note that `GlobalExceptionHandler` in `common/exception/` is the other
half of this story — it catches any exception thrown anywhere in the
application and automatically converts it into an `ErrorResponse`. The
two packages work together: `response/` defines the shapes,
`exception/` ensures errors always land in the right shape.

---

## Glossary

| Term | Meaning |
|---|---|
| `ApiResponse<T>` | The envelope wrapping every successful API response |
| `ErrorResponse` | The envelope wrapping every failed API response |
| Generic (`<T>`) | A placeholder type — `ApiResponse<SearchResult>` means `data` holds a `SearchResult` |
| Envelope pattern | Wrapping a payload in a consistent outer structure so every response looks the same |
| Cross-cutting concern | Logic that spans many features and belongs to no single one — lives in `common/` |
| `GlobalExceptionHandler` | Catches all exceptions and converts them to `ErrorResponse` automatically |

---

## One-Paragraph Summary

`common/response/` exists because without it every controller invents
its own response shape, and the frontend has to learn a different
structure for every endpoint. `ApiResponse<T>` wraps every successful
response in a consistent envelope — `success`, `message`, and `data` —
where `data` holds whatever the endpoint actually returns. `ErrorResponse`
wraps every failure with `success`, `error`, `message`, and `timestamp`,
so the frontend always knows where to find the error code and what to
show the user. The package lives in `common/` because every controller
in the application uses it — it belongs to no single domain, it is
shared infrastructure. The domain layer stays clean and unaware of the
envelope; controllers do the wrapping as the last step before sending a
response out.