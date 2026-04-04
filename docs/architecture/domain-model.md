# Domain Model

## Classification Guide

### Entity vs Value Object

> **"Do I need to distinguish this object from another identical one?"**
> - **YES** → Entity (has a unique `UUID id`)
> - **NO** → Value Object (defined purely by its data that's why it should be immutable by design in almost all cases)

Two hotels with the same name are still different hotels → `Hotel` is an Entity.
Two addresses with identical fields are interchangeable → `Address` is a Value Object.

### Enums

Fixed sets of named constants — no fields or behavior. Grouped separately
so valid values for a concept are easy to locate.

### Query / Result Records

Communication contracts between layers — not domain concepts:
- **Query records** — input contracts (what a caller must provide)
- **Result records** — output projections (shaped for a specific use case)

In a future microservices architecture these become API request/response DTOs.

| Category | Purpose |
|---|---|
| Entities | Domain objects with identity (`UUID id`) |
| Value Objects | Domain objects defined purely by their data |
| Enums | Fixed sets of valid constants |
| Query / Result Records | Input/output contracts between layers |
| Infrastructure Abstractions | Interfaces that decouple application logic from external services |

---

## Entity Overview

```
Country
 └── isoCode, phoneCode, currencyCode

City
 ├── countryId → Country
 ├── timezone
 └── coordinates → Coordinates

User
 ├── email, password (hashed)
 ├── phoneNumber → PhoneNumber (optional)
 ├── primaryImageId → Image (optional)
 ├── preferredCurrency, role, status
 └── createdAt / updatedAt

RefreshToken
 ├── userId → User
 ├── token (opaque UUID string, unique)
 ├── deviceName (optional — e.g. "iPhone 15")
 ├── expiresAt
 ├── revoked
 └── createdAt

Address (value object)
 ├── street, area (optional), zipCode
 └── cityId → City

Hotel
 ├── address → Address
 ├── phoneNumber → PhoneNumber
 ├── rating (derived from reviews)
 ├── status → HotelStatus
 ├── ownerId → User (HOTEL_ADMIN role)
 ├── primaryImageId → Image (optional)
 ├── amenities → List<Amenity>
 └── List<RoomType>
       ├── category → RoomCategory
       ├── bedTypes → List<BedType>
       ├── capacity, totalRooms
       ├── status → RoomTypeStatus
       ├── primaryImageId → Image (optional)
       └── List<RatePolicy>
             ├── pricePerNight, currencyCode
             └── discountPolicy → DiscountPolicy (optional)

Booking
 ├── bookingGroupId (links multiple room type bookings)
 ├── hotelId → Hotel
 ├── roomTypeId → RoomType
 ├── userId → User
 ├── rooms, guestCount
 ├── checkIn / checkOut: LocalDate
 ├── status → BookingStatus
 ├── totalPrice (snapshotted), currencyCode
 ├── cancelledAt / cancellationReason (only if CANCELLED)
 └── createdAt / updatedAt

Review
 ├── bookingId → Booking (one review per booking)
 ├── userId → User
 ├── hotelId → Hotel
 ├── rating → ReviewRating
 ├── status → ReviewStatus
 ├── comment
 └── createdAt / updatedAt

SearchHistory
 ├── userId → User
 ├── cityId → City
 ├── checkIn / checkOut, guestCount
 ├── status → SearchHistoryStatus
 └── createdAt

Destination
 ├── cityId → City
 ├── name, description
 ├── primaryImageId → Image (optional)
 ├── searchCount, bookingCount (popularity)
 ├── status → DestinationStatus
 └── createdAt / updatedAt

Image
 ├── entityId, entityType → ImageEntityType
 ├── url, confirmed, status → ImageStatus
 └── createdAt / updatedAt

Notification
 ├── userId → User
 ├── hotelId → Hotel (optional)
 ├── type → NotificationType
 ├── channel → Channel
 ├── status → NotificationStatus
 ├── readStatus → NotificationReadStatus
 ├── scheduledAt (optional), sentAt (null until SENT)
 └── createdAt / updatedAt

Promotion
 ├── code (unique), description
 ├── scope → PromotionScope
 ├── userId → User (USER scope only)
 ├── hotelId → Hotel (HOTEL scope only)
 ├── discountType → DiscountType
 ├── discountValue
 ├── usageLimit, usageLimitPerUser, usageCount
 ├── validFrom / validTo (expiry derived from validTo)
 ├── status → PromotionStatus
 └── createdAt / updatedAt

Payment
 ├── bookingId → Booking
 ├── userId → User
 ├── amount, currencyCode
 ├── paymentMethod → PaymentMethod
 ├── status → PaymentStatus
 ├── gatewayTransactionId, gatewayProvider
 └── createdAt / updatedAt

Refund
 ├── paymentId → Payment
 ├── amount, currencyCode
 ├── status → RefundStatus
 ├── reason, gatewayRefundId
 └── createdAt / updatedAt
```

---

## Entities

### `Country`

```java
public record Country(
    UUID id,
    String name,
    String isoCode,        // ISO 3166-1 alpha-2, e.g. "BD", "TH"
    String phoneCode,      // e.g. "+880"
    String currencyCode    // ISO 4217, e.g. "BDT"
) {}
```

**Validation:** `isoCode` — 2 uppercase chars. `currencyCode` — 3 uppercase chars.
`phoneCode` — starts with `+` followed by digits.

---

### `City`

```java
public record City(
    UUID id,
    String name,
    String timezone,            // IANA, e.g. "Asia/Dhaka"
    Coordinates coordinates,
    UUID countryId
) {}
```

**Validation:** `timezone` — valid IANA string. `countryId` — must reference existing `Country`.

---

### `User`

```java
public record User(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String password,            // hashed — never plaintext
    PhoneNumber phoneNumber,    // optional
    UUID primaryImageId,        // optional
    String preferredCurrency,   // ISO 4217
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation:** `email` — valid format, unique. `password` — hashed. `preferredCurrency` — 3 uppercase chars.
`role` defaults to `GUEST`. `status` defaults to `INACTIVE` (activated after email verification).

---

### `RefreshToken`

```java
public record RefreshToken(
    UUID id,
    UUID userId,
    String token,               // opaque UUID string — not a JWT
    String deviceName,          // optional — e.g. "iPhone 15", "MacBook Pro"
    LocalDateTime expiresAt,
    boolean revoked,
    LocalDateTime createdAt
) {}
```

**Validation:** `token` must be unique. `expiresAt` must be in the future on creation.
`revoked` defaults to `false`. One row per active session — a user may have multiple rows
(one per device). Revoked or expired tokens are never deleted immediately; a cleanup
scheduler purges them periodically.

---

### `Hotel`

```java
public record Hotel(
    UUID id,
    String name,
    Address address,
    double rating,              // derived from reviews — never set manually
    HotelStatus status,
    String description,
    PhoneNumber phoneNumber,
    List<Amenity> amenities,
    List<RoomType> roomTypes,
    UUID primaryImageId,        // optional
    UUID ownerId                // references User with HOTEL_ADMIN role
) {}
```

**Validation:** `status` defaults to `PENDING`. `rating` defaults to `0.0`.
`ownerId` must reference a `User` with `HOTEL_ADMIN` role.

---

### `RoomType`

```java
public record RoomType(
    UUID id,
    String name,
    RoomCategory category,
    List<BedType> bedTypes,
    int totalRooms,
    int capacity,
    RoomTypeStatus status,
    UUID primaryImageId,        // optional
    List<RatePolicy> ratePolicies
) {}
```

**Validation:** `totalRooms` ≥ 1. `capacity` ≥ 1. `bedTypes` not empty.
`ratePolicies` must not have overlapping date ranges.

---

### `Booking`

```java
public record Booking(
    UUID id,
    UUID bookingGroupId,        // links multiple room type bookings
    UUID hotelId,
    UUID roomTypeId,
    UUID userId,
    int rooms,
    int guestCount,
    LocalDate checkIn,
    LocalDate checkOut,
    BookingStatus status,
    BigDecimal totalPrice,      // snapshotted at creation
    String currencyCode,
    LocalDateTime cancelledAt,  // null unless CANCELLED
    String cancellationReason,  // null unless CANCELLED
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation:** `checkOut` > `checkIn`. `guestCount` ≥ 1. `rooms` ≥ 1. `totalPrice` > 0.

---

### `Review`

```java
public record Review(
    UUID id,
    UUID bookingId,
    UUID userId,
    UUID hotelId,
    ReviewRating rating,
    ReviewStatus status,
    String comment,
    LocalDateTime createdAt,
    LocalDateTime updatedAt     // null until first edit
) {}
```

**Validation:** `bookingId` must reference a `COMPLETED` booking. One review per `bookingId`.
All `ReviewRating` fields between 1 and 10.

---

### `SearchHistory`

```java
public record SearchHistory(
    UUID id,
    UUID userId,
    UUID cityId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guestCount,
    SearchHistoryStatus status,
    LocalDateTime createdAt
) {}
```

**Validation:** `checkOut` > `checkIn`. `guestCount` ≥ 1.

---

### `Destination`

```java
public record Destination(
    UUID id,
    UUID cityId,
    String name,
    String description,
    UUID primaryImageId,        // optional
    int searchCount,
    int bookingCount,
    DestinationStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Popularity score:** `searchCount + (bookingCount * 2)`

---

### `Image`

```java
public record Image(
    UUID id,
    UUID entityId,
    ImageEntityType entityType,
    String url,
    boolean confirmed,          // false until confirmImage() called
    ImageStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation:** `confirmed` defaults to `false`. Only `ACTIVE` confirmed images returned publicly.

---

### `Notification`

```java
public record Notification(
    UUID id,
    UUID userId,
    UUID hotelId,               // optional
    NotificationType type,
    String subject,
    String body,
    Channel channel,
    NotificationStatus status,
    NotificationReadStatus readStatus,
    LocalDateTime scheduledAt,  // null means send immediately
    LocalDateTime sentAt,       // null until SENT
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation:** `status` defaults to `PENDING`. `readStatus` defaults to `UNREAD`.
Only `PENDING` notifications can be cancelled.

---

### `Promotion`

```java
public record Promotion(
    UUID id,
    String code,                // unique
    String description,
    PromotionScope scope,
    UUID userId,                // null unless USER scope
    UUID hotelId,               // null unless HOTEL scope
    DiscountType discountType,
    BigDecimal discountValue,
    int usageLimit,
    int usageLimitPerUser,
    int usageCount,
    LocalDate validFrom,
    LocalDate validTo,          // expiry derived — not stored as status
    PromotionStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation:** `code` unique. `validTo` > `validFrom`. `usageLimit` ≥ 1.
`usageCount` defaults to 0.

---

### `Payment`

```java
public record Payment(
    UUID id,
    UUID bookingId,
    UUID userId,
    BigDecimal amount,
    String currencyCode,
    PaymentMethod paymentMethod,
    PaymentStatus status,
    String gatewayTransactionId,
    String gatewayProvider,     // e.g. "STRIPE"
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation:** `amount` > 0. `status` defaults to `PENDING`.
`amount` stored independently from `Booking.totalPrice`.

---

### `Refund`

```java
public record Refund(
    UUID id,
    UUID paymentId,
    BigDecimal amount,
    String currencyCode,
    RefundStatus status,
    String reason,
    String gatewayRefundId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation:** `amount` > 0 and ≤ `payment.amount`. `currencyCode` matches `payment.currencyCode`.
Can only refund `COMPLETED` payments.

---

## Value Objects

### `Address`

```java
public record Address(
    String street,
    String area,        // optional — neighborhood/district
    String zipCode,
    UUID cityId
) {}
```

### `Coordinates`

```java
public record Coordinates(
    double latitude,
    double longitude
) {}
```

### `PhoneNumber`

```java
public record PhoneNumber(
    String countryCode,    // e.g. "+880"
    String number          // e.g. "1712345678"
) {}
```

### `RatePolicy`

```java
public record RatePolicy(
    LocalDate validFrom,
    LocalDate validTo,
    BigDecimal pricePerNight,
    String currencyCode,
    DiscountPolicy discountPolicy    // optional
) {}
```

See [ADR-001](decisions/ADR-001-rate-policy.md).

### `DiscountPolicy`

```java
public record DiscountPolicy(
    DiscountType type,
    BigDecimal value,      // percentage (20.0) or fixed amount (10.00)
    String reason          // e.g. "Early Bird"
) {}
```

**Effective price:**
- `PERCENTAGE`: `pricePerNight * (1 - value / 100)`
- `FIXED`: `pricePerNight - value`

### `ReviewRating`

```java
public record ReviewRating(
    double cleanliness,     // 1-10
    double facilities,      // 1-10
    double location,        // 1-10
    double service,         // 1-10
    double valueForMoney    // 1-10
) {}
```

---

## Enums

### Hotel & Room

```java
public enum HotelStatus   { ACTIVE, INACTIVE, PENDING }
public enum RoomTypeStatus { ACTIVE, INACTIVE }
public enum RoomCategory  { STANDARD, DELUXE, SUITE, VILLA }
public enum BedType       { SINGLE, DOUBLE, TWIN, QUEEN, KING }
public enum Amenity       { POOL, WIFI, GYM, PARKING, SPA, RESTAURANT }
```

### User & Auth

```java
public enum UserRole   { GUEST, HOTEL_ADMIN, HOTEL_MANAGER, SUPER_ADMIN }
public enum UserStatus { ACTIVE, INACTIVE, BANNED }
```

### Booking & Payment

```java
public enum BookingStatus { CONFIRMED, COMPLETED, CANCELLED }
public enum PaymentStatus { PENDING, CONFIRMED, COMPLETED, CANCELLED, REFUNDED }
public enum RefundStatus  { PENDING, COMPLETED, FAILED }
public enum PaymentMethod { CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, DIGITAL_WALLET }
```

### Reviews & Notifications

```java
public enum ReviewStatus          { ACTIVE, INACTIVE }
public enum NotificationStatus    { PENDING, SENT, FAILED, CANCELLED }
public enum NotificationReadStatus { READ, UNREAD }
public enum NotificationType {
    BOOKING_CONFIRMATION, BOOKING_CANCELLATION, BOOKING_EDIT,
    EMAIL_VERIFICATION, PASSWORD_RESET, REVIEW_REQUEST
}
public enum Channel { EMAIL }    // SMS, PUSH, IN_APP deferred
```

### Search & Destination

```java
public enum SearchHistoryStatus { ACTIVE, INACTIVE }
public enum DestinationStatus   { ACTIVE, INACTIVE }
```

### Images

```java
public enum ImageStatus     { ACTIVE, INACTIVE }
public enum ImageEntityType { HOTEL, ROOM_TYPE, DESTINATION, USER }
```

### Pricing & Promotions

```java
public enum DiscountType   { PERCENTAGE, FIXED }
public enum PromotionScope  { GLOBAL, USER, HOTEL }
public enum PromotionStatus { ACTIVE, INACTIVE }
```

### Admin

```java
public enum RevenueScopeType  { SYSTEM, HOTEL, CITY }
public enum ContentType       { REVIEW, IMAGE, NOTIFICATION, DESTINATION }
public enum ModerationAction  { ACTIVATE, DEACTIVATE }
```

---

## Query / Result Records

### Search

```java
public record CitySearchQuery(
    UUID userId,                       // optional — null = anonymous
    UUID cityId,
    LocalDate checkIn, LocalDate checkOut,
    int guestCount,
    List<Amenity> amenities,           // optional
    List<RoomCategory> categories,     // optional
    List<BedType> bedTypes             // optional
) {}

public record HotelSearchQuery(
    UUID hotelId,
    LocalDate checkIn, LocalDate checkOut,
    int guestCount
) {}

public record HotelSummary(
    UUID hotelId, String name,
    Address address, double rating,
    BigDecimal startingFromPrice
) {}

public record SearchResult(
    List<HotelSummary> hotels,
    List<HotelSummary> suggestions,
    int page, int size, long totalResults
) {}
```

### Hotel Management

```java
public record AddHotelRequest(
    String name, Address address, String description,
    PhoneNumber phoneNumber, List<Amenity> amenities, UUID ownerId
) {}

public record EditHotelRequest(
    Optional<String> name, Optional<Address> address,
    Optional<String> description, Optional<PhoneNumber> phoneNumber,
    Optional<List<Amenity>> amenities
) {}

public record AddRoomTypeRequest(
    String name, RoomCategory category, List<BedType> bedTypes,
    int totalRooms, int capacity
) {}

public record EditRoomTypeRequest(
    Optional<String> name, Optional<RoomCategory> category,
    Optional<List<BedType>> bedTypes, Optional<Integer> totalRooms,
    Optional<Integer> capacity
) {}

public record AddRatePolicyRequest(
    LocalDate validFrom, LocalDate validTo,
    BigDecimal pricePerNight, String currencyCode,
    DiscountPolicy discountPolicy
) {}

public record EditRatePolicyRequest(
    Optional<LocalDate> validFrom, Optional<LocalDate> validTo,
    Optional<BigDecimal> pricePerNight, Optional<String> currencyCode,
    Optional<DiscountPolicy> discountPolicy
) {}
```

### Availability & Revenue

```java
public record RoomTypeAvailability(
    UUID roomTypeId, String roomTypeName,
    int totalRooms, int bookedRooms, int availableRooms,
    AvailabilityStatus status
) {}

public enum AvailabilityStatus { AVAILABLE, PARTIALLY_BOOKED, FULLY_BOOKED }

public record RevenueScope(
    RevenueScopeType type,
    UUID scopeId              // null for SYSTEM
) {}

public record Revenue(
    UUID hotelId, RevenuePeriod period,
    Map<String, BigDecimal> revenuePerCurrency,
    LocalDateTime from, LocalDateTime to
) {}

public enum RevenuePeriod { DAY, WEEK, MONTH, YEAR }

public record OccupancyRate(
    UUID hotelId, double rate,
    int totalRooms, int occupiedRooms,
    LocalDate asOf
) {}
```

### Booking

```java
public record CreateBookingRequest(
    UUID hotelId, UUID roomTypeId, UUID userId,
    int rooms, int guestCount,
    LocalDate checkIn, LocalDate checkOut
) {}

public record EditBookingRequest(
    Optional<LocalDate> checkIn,
    Optional<LocalDate> checkOut,
    Optional<Integer> guestCount
) {}

public record BookingSummary(
    UUID bookingId, String hotelName, String roomTypeName,
    LocalDate checkIn, LocalDate checkOut,
    BookingStatus status, BigDecimal totalPrice, String currencyCode
) {}
```

### User & Auth

```java
public record RegisterRequest(
    String firstName, String lastName, String email, String password,
    PhoneNumber phoneNumber, String preferredCurrency
) {}

public record EditUserRequest(
    Optional<String> firstName, Optional<String> lastName,
    Optional<PhoneNumber> phoneNumber, Optional<UUID> primaryImageId,
    Optional<String> preferredCurrency
) {}

public record AuthRequest(String email, String password) {}

public record AuthResponse(
    String accessToken, String refreshToken,
    LocalDateTime expiresAt
) {}

public record ChangePasswordRequest(
    String currentPassword, String newPassword
) {}

public record UserSummary(
    UUID userId, String firstName, String lastName,
    String email, UserRole role, UserStatus status,
    LocalDateTime createdAt
) {}
```

### Reviews

```java
public record CreateReviewRequest(
    UUID bookingId, UUID userId, UUID hotelId,
    ReviewRating rating, String comment
) {}

public record EditReviewRequest(
    Optional<ReviewRating> rating,
    Optional<String> comment
) {}
```

### Images

```java
public record ImageUploadRequest(
    UUID entityId, ImageEntityType entityType,
    byte[] data, String fileName, String contentType
) {}
```

### Notifications

```java
public record CreateNotificationRequest(
    UUID userId, UUID hotelId,
    NotificationType type, String subject, String body,
    Channel channel, LocalDateTime scheduledAt
) {}
```

### Promotions

```java
public record CreatePromotionRequest(
    String code, String description,
    PromotionScope scope, UUID hotelId,
    DiscountType discountType, BigDecimal discountValue,
    int usageLimit, int usageLimitPerUser,
    LocalDate validFrom, LocalDate validTo
) {}

public record EditPromotionRequest(
    Optional<String> description,
    Optional<BigDecimal> discountValue,
    Optional<Integer> usageLimit, Optional<Integer> usageLimitPerUser,
    Optional<LocalDate> validFrom, Optional<LocalDate> validTo
) {}

public record ValidatePromotionResult(
    UUID promotionId, String code,
    DiscountType discountType, BigDecimal discountValue,
    BigDecimal discountAmount, BigDecimal finalPrice
) {}
```

### Destinations

```java
public record AddDestinationRequest(
    String name, String description
) {}

public record EditDestinationRequest(
    Optional<String> name,
    Optional<String> description,
    Optional<UUID> primaryImageId
) {}
```

### Payments

```java
public record CreatePaymentRequest(
    UUID bookingId, UUID userId,
    BigDecimal amount, String currencyCode,
    PaymentMethod paymentMethod
) {}
```

### Admin

```java
public record SystemStats(
    long totalUsers, long totalHotels,
    long totalBookings, long totalReviews,
    long activePromotions,
    Map<UserStatus, Long> usersByStatus,
    Map<BookingStatus, Long> bookingsByStatus,
    LocalDateTime asOf
) {}
```

---

## Invariants

### Identity & Users
- `User.email` must be unique across all users
- `User.password` is never stored or returned as plaintext
- `User.role` defaults to `GUEST` on registration
- `User.status` defaults to `INACTIVE` — activated after email verification
- A `BANNED` user cannot create bookings or write reviews
- Deleted accounts are anonymized — personal data replaced, status set to `INACTIVE`
- `RefreshToken.token` must be unique across all tokens
- A user may have multiple active `RefreshToken` rows — one per device session
- On logout, the corresponding `RefreshToken` is marked `revoked = true`
- On password change or account suspension, all `RefreshToken` rows for that user are revoked
- A revoked or expired `RefreshToken` is rejected at the refresh endpoint
- If a revoked token is used, the account should be flagged for suspicious activity

### Geography
- A `City` must reference an existing `Country`
- A `Hotel.address` must reference an existing `City`

### Hotels & Rooms
- A `Hotel` must reference a `User` with `HOTEL_ADMIN` role via `ownerId`
- Only `ACTIVE` hotels appear in search results
- Only `ACTIVE` room types are included in search results
- `Hotel.rating` is always derived from reviews — never set manually
- A `RatePolicy` must not have overlapping date ranges within the same `RoomType`
- For `PERCENTAGE` discount — value must be between 0 and 100 exclusive
- For `FIXED` discount — discount must not exceed `pricePerNight`

### Bookings
- `checkOut` must be strictly after `checkIn`
- `guestCount` must be ≤ the room type's `capacity`
- `rooms` must be ≤ available rooms for the requested date range
- A room type cannot be double-booked: concurrent bookings cannot exceed `totalRooms`
- `totalPrice` is snapshotted at booking creation — never recalculated after
- `cancelledAt` and `cancellationReason` are only populated when status is `CANCELLED`
- Bookings sharing a `bookingGroupId` belong to the same transaction

### Reviews
- A review can only be submitted for a `COMPLETED` booking
- One review per `bookingId` — uniqueness enforced on `bookingId`
- A guest may review the same hotel multiple times with multiple completed bookings
- All `ReviewRating` fields must be between 1 and 10 inclusive
- Only `ACTIVE` reviews returned in public queries
- Admin uses `deactivateReview` to hide reviews — hard deletes not permitted

### Images
- Only `ACTIVE` confirmed images returned in public queries
- `primaryImageId` is optional — entities can exist without a primary image

### Notifications
- Notifications created as `PENDING` — dispatched asynchronously
- Only `PENDING` notifications can be cancelled
- `sentAt` is null until status transitions to `SENT`
- `readStatus` defaults to `UNREAD` on creation

### Promotions
- Promotion `code` must be unique across all promotions
- Promotion expiry derived from `validTo` — never stored as status
- `usageCount` must never exceed `usageLimit`
- A user's usage must never exceed `usageLimitPerUser`
- `userId` required when scope is `USER`, null otherwise
- `hotelId` required when scope is `HOTEL`, null otherwise

### Payments
- `Payment.amount` stored independently from `Booking.totalPrice`
- Refunds can only be processed for `COMPLETED` payments
- `Refund.amount` must not exceed `Payment.amount`
- `Refund.currencyCode` must match `Payment.currencyCode`
- `gatewayTransactionId` never null after payment is `CONFIRMED`

### Destinations
- `popularityScore` = `searchCount` + (`bookingCount` * 2)
- Only `ACTIVE` destinations appear in `getPopularDestinations`

### Admin
- `RevenueScope.scopeId` is null when type is `SYSTEM`
- All moderation actions go through `AdminService.moderateContent()`
- `OccupancyRate.rate` is always between 0.0 and 1.0 inclusive

---

## Infrastructure Abstractions

Interfaces defined by the application layer and implemented by the infrastructure layer.
The application never imports a concrete class — only the interface. This means the
underlying provider (Stripe, S3, SendGrid) can be swapped without touching business logic.

### `PaymentGateway`

```java
public interface PaymentGateway {

    // Charges the given amount and returns a gateway transaction ID on success.
    // Throws PaymentFailedException if the charge is declined or the gateway errors.
    PaymentGatewayResult charge(CreatePaymentRequest request);

    // Initiates a refund against a previously confirmed transaction.
    // Throws RefundFailedException if the refund cannot be processed.
    RefundGatewayResult refund(String gatewayTransactionId, BigDecimal amount, String currencyCode);
}
```

**Implemented by:** `StripePaymentGateway`, `OmisePaymentGateway` (or any provider).
**Used by:** `PaymentService` — never called directly from a controller or scheduler.
**Invariant:** The gateway is never called unless `Booking.status` is `CONFIRMED` and
`Payment.status` is `PENDING`.

---

### `StorageGateway`

```java
public interface StorageGateway {

    // Uploads raw bytes and returns a publicly accessible URL.
    // Throws ImageUploadException if the upload fails.
    String upload(ImageUploadRequest request);

    // Permanently deletes a file by its URL.
    // Silent no-op if the file does not exist.
    void delete(String url);
}
```

**Implemented by:** `S3StorageGateway`, `GcsStorageGateway`, `LocalStorageGateway` (dev only).
**Used by:** `ImageService` — the rest of the application only ever sees the returned URL.
**Invariant:** `Image.confirmed` is set to `true` only after `StorageGateway.upload()` returns
successfully. An unconfirmed image is never returned in public queries.

---

### `EmailGateway`

```java
public interface EmailGateway {

    // Sends a single email immediately.
    // Throws EmailDeliveryException if the provider rejects the request.
    void send(EmailMessage message);
}
```

**Implemented by:** `SendGridEmailGateway`, `SesEmailGateway`, `MockEmailGateway` (test only).
**Used by:** `NotificationService` — only when `Notification.channel` is `EMAIL` and
`Notification.status` is `PENDING`.
**Invariant:** `Notification.sentAt` is populated and `status` transitions to `SENT` only
after `EmailGateway.send()` returns without throwing. On failure, `status` transitions
to `FAILED` and the notification is eligible for retry.