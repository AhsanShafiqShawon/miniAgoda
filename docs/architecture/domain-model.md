# Domain Model

## Classification Guide

### Entity vs Value Object

When modeling a class, ask:

> **"Do I need to distinguish this object from another identical one?"**
> - **YES** → Entity (has a unique `UUID id`)
> - **NO** → Value Object (defined purely by its data)

For example: two hotels with the same name are still different hotels → `Hotel` is an Entity.
Two addresses with identical fields are interchangeable → `Address` is a Value Object.

### Enums

Enums are neither entities nor value objects — they represent a fixed set of
named constants with no fields or behavior. Grouped separately so valid values
for a given concept (e.g. hotel status, bed type) are easy to locate.

### Query / Result Records

These are not domain concepts — they are **communication contracts between layers**:

- Query records (e.g. `CitySearchQuery`) — input contracts, what a caller must provide
- Result records (e.g. `SearchResult`, `HotelSummary`) — output contracts, projections
  of domain data shaped for a specific use case

A `Hotel` exists in the domain regardless of any search.
A `HotelSummary` only exists because someone asked a question.

In a future microservices architecture, query/result records become API request/response
DTOs that cross service boundaries — keeping them separate now prepares for that split.

| Category | Purpose |
|---|---|
| Entities | Domain objects with identity |
| Value Objects | Domain objects defined purely by their data |
| Enums | Fixed sets of valid constants |
| Query / Result Records | Input/output contracts between layers |

---

## Entity Overview

```
Country
 └── isoCode, phoneCode, currencyCode

City
 ├── countryId → Country
 ├── timezone
 └── coordinates → Coordinates

Address
 ├── street, area, zipCode
 └── cityId → City

PhoneNumber
 └── countryCode, number

Hotel
 ├── address → Address
 ├── phoneNumber → PhoneNumber
 ├── rating (derived from reviews)
 ├── status → HotelStatus
 ├── ownerId → User
 ├── amenities → List<Amenity>
 └── List<RoomType>
       ├── category → RoomCategory
       ├── bedTypes → List<BedType>
       ├── capacity: int
       ├── totalRooms: int
       ├── status → RoomTypeStatus
       ├── imageUrls: List<String>
       └── List<RatePolicy>
             ├── pricePerNight: BigDecimal
             ├── currencyCode: String
             └── discountPolicy → DiscountPolicy (optional)

Booking
 ├── hotelId → Hotel
 ├── roomTypeId → RoomType
 ├── userId → User
 ├── status → BookingStatus
 ├── totalPrice: BigDecimal (snapshotted at creation)
 ├── currencyCode: String
 ├── checkIn / checkOut: LocalDate
 ├── cancelledAt / cancellationReason (only if CANCELLED)
 └── createdAt / updatedAt: LocalDateTime

Review
 ├── bookingId → Booking (one review per booking)
 ├── userId → User
 ├── hotelId → Hotel
 ├── rating → ReviewRating
 ├── comment: String
 └── createdAt / updatedAt: LocalDateTime

Destination
 ├── cityId → City
 ├── name, description
 ├── imageUrls: List<String>
 ├── searchCount, bookingCount
 ├── status → DestinationStatus
 └── createdAt / updatedAt: LocalDateTime

RoomTypeAvailability (result record)
 ├── roomTypeId, roomTypeName
 ├── totalRooms, bookedRooms, availableRooms
 └── status → AvailabilityStatus

Revenue (result record)
 ├── hotelId, period → RevenuePeriod
 ├── revenuePerCurrency: Map<String, BigDecimal>
 └── from / to: LocalDateTime

OccupancyRate (result record)
 ├── hotelId, rate: double
 ├── totalRooms, occupiedRooms
 └── asOf: LocalDate
```

---

## Entities

### `Country`

Represents a country that cities belong to. Holds standardized codes for
internationalization and display purposes.

```java
public record Country(
    UUID id,
    String name,
    String isoCode,        // ISO 3166-1 alpha-2, e.g. "BD", "TH", "SG"
    String phoneCode,      // e.g. "+880", "+66"
    String currencyCode    // ISO 4217, e.g. "BDT", "THB", "USD"
) {}
```

**Validation rules (service layer):**
- `isoCode` — exactly 2 uppercase characters
- `currencyCode` — exactly 3 uppercase characters
- `phoneCode` — starts with `+` followed by digits

---

### `City`

Represents a destination that hotels belong to and the target of a
city-based search. References `Country` by ID only — no embedded object.

```java
public record City(
    UUID id,
    String name,
    String timezone,            // IANA timezone, e.g. "Asia/Dhaka"
    Coordinates coordinates,
    UUID countryId
) {}
```

**Validation rules (service layer):**
- `timezone` — valid IANA timezone string
- `coordinates` — must not be null
- `countryId` — must reference an existing `Country`

---

### `Hotel`

Represents a property. Holds metadata and a list of `RoomType`s.
Hotels do not hold bookings — bookings are managed by `BookingRepository`.
Only `ACTIVE` hotels appear in search results — `INACTIVE` and `PENDING`
hotels are automatically excluded by `HotelSearchService`.

```java
public record Hotel(
    UUID id,
    String name,
    Address address,
    double rating,              // derived from reviews, updated by ReviewService
    HotelStatus status,
    String description,
    PhoneNumber phoneNumber,
    List<Amenity> amenities,
    List<RoomType> roomTypes,
    UUID ownerId                // references User with HOTEL_OWNER role
) {}
```

**Validation rules (service layer):**
- `name` — must not be blank
- `address` — must not be null
- `phoneNumber` — must not be null
- `status` — must not be null, defaults to `PENDING` on creation
- `ownerId` — must reference an existing `User` with `HOTEL_OWNER` role
- `rating` — defaults to `0.0` on creation

---

### `RoomType`

Represents a category of rooms within a hotel (e.g., "Deluxe Double",
"Suite"). The unit of availability and pricing in miniAgoda. Availability
is modeled at this level — we track how many rooms of a given type are
bookable, not individual physical room numbers.

```java
public record RoomType(
    UUID id,
    String name,
    RoomCategory category,
    List<BedType> bedTypes,
    int totalRooms,
    int capacity,
    RoomTypeStatus status,
    List<String> imageUrls,
    List<RatePolicy> ratePolicies
) {}
```

**Validation rules (service layer):**
- `name` — must not be blank
- `totalRooms` — must be ≥ 1
- `capacity` — must be ≥ 1
- `category` — must not be null
- `bedTypes` — must not be empty
- `ratePolicies` — must not have overlapping date ranges

See [ADR-002](decisions/ADR-002-availability-per-room-type.md) for why
availability is modeled per room type.

---

### `Booking`

An immutable record representing a confirmed reservation. Created by
`BookingService` after availability and capacity checks pass. Price is
snapshotted at creation — never recalculated after.

```java
public record Booking(
    UUID id,
    UUID hotelId,
    UUID roomTypeId,
    UUID userId,
    int guestCount,
    LocalDate checkIn,
    LocalDate checkOut,
    BookingStatus status,
    BigDecimal totalPrice,      // snapshotted at booking creation
    String currencyCode,        // ISO 4217
    LocalDateTime cancelledAt,  // null unless CANCELLED
    String cancellationReason,  // null unless CANCELLED
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Validation rules (service layer):**
- `checkOut` must be strictly after `checkIn`
- `guestCount` must be ≥ 1
- `totalPrice` must be > 0
- `currencyCode` — exactly 3 uppercase characters
- `cancelledAt` and `cancellationReason` — null unless status is `CANCELLED`
- `createdAt` — set on creation, never updated
- `updatedAt` — updated on every state change

---

### `Review`

Represents a guest's rating and feedback for a hotel after a completed stay.
One review per booking — a guest may review the same hotel multiple times
if they have multiple completed bookings.

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

**Validation rules (service layer):**
- `bookingId` — must reference a `COMPLETED` booking
- One review per `bookingId` — enforced before creation
- `comment` — must not be blank
- All `ReviewRating` fields — must be between 1 and 10 inclusive
- `createdAt` — set on creation, never updated
- `updatedAt` — set on every edit, null until first edit

---

### `SearchHistory`

Represents a recorded search query made by a user. Only city-based searches
are recorded — drill-down searches (`HotelSearchQuery`) are not recorded.
Anonymous searches (null `userId`) are never recorded.

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

**Validation rules (service layer):**
- `userId` — must reference an existing `User`
- `cityId` — must reference an existing `City`
- `checkOut` must be strictly after `checkIn`
- `guestCount` must be ≥ 1
- `createdAt` — set on creation, never updated

---

### `Destination`

A curated point of interest wrapping a city with popularity metadata.
Used for the "Popular Destinations" feature. Popularity is derived from
search and booking activity — bookings are weighted higher than searches.

```java
public record Destination(
    UUID id,
    UUID cityId,
    String name,
    String description,
    List<String> imageUrls,
    int searchCount,
    int bookingCount,
    DestinationStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Popularity score:**
```java
popularityScore = searchCount + (bookingCount * 2)
```

**Who updates counts:**
- `searchCount` — incremented asynchronously by `SearchHistoryService.recordSearch()`
- `bookingCount` — incremented asynchronously by `BookingService.createBooking()`

**Validation rules (service layer):**
- `cityId` — must reference an existing `City`
- `name` — must not be blank
- `createdAt` — set on creation, never updated
- `updatedAt` — updated on every state change

---

## Enums

### `HotelStatus`

```java
public enum HotelStatus {
    ACTIVE,     // visible in search results
    INACTIVE,   // temporarily closed, excluded from search
    PENDING     // awaiting activation, excluded from search
}
```

### `Amenity`

```java
public enum Amenity {
    POOL,
    WIFI,
    GYM,
    PARKING,
    SPA,
    RESTAURANT
}
```

### `RoomCategory`

```java
public enum RoomCategory {
    STANDARD,
    DELUXE,
    SUITE,
    VILLA
}
```

### `RoomTypeStatus`

```java
public enum RoomTypeStatus {
    ACTIVE,     // visible and bookable
    INACTIVE    // hidden and not bookable
}
```

### `BedType`

```java
public enum BedType {
    SINGLE,
    DOUBLE,
    TWIN,
    QUEEN,
    KING
}
```

### `DiscountType`

```java
public enum DiscountType {
    PERCENTAGE,    // e.g. 20.0 means 20% off pricePerNight
    FIXED          // e.g. 10.00 fixed amount off pricePerNight
}
```

### `BookingStatus`

```java
public enum BookingStatus {
    CONFIRMED,    // booking created and confirmed instantly
    COMPLETED,    // automatically set after checkOut date passes
    CANCELLED     // cancelled by guest or system
}
```

### `ReviewStatus`

```java
public enum ReviewStatus {
    ACTIVE,     // visible publicly
    INACTIVE    // hidden by admin moderation
}
```

### `SearchHistoryStatus`

```java
public enum SearchHistoryStatus {
    ACTIVE,     // visible in user's search history
    INACTIVE    // hidden by user
}
```

### `DestinationStatus`

```java
public enum DestinationStatus {
    ACTIVE,     // visible publicly in popular destinations
    INACTIVE    // hidden
}
```

### `AvailabilityStatus`

```java
public enum AvailabilityStatus {
    AVAILABLE,          // rooms still available for booking
    PARTIALLY_BOOKED,   // some rooms booked, some available
    FULLY_BOOKED        // all rooms booked for the period
}
```

### `RevenuePeriod`

```java
public enum RevenuePeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR
}
```

---

## Value Objects

### `RatePolicy`

A date-range-based pricing rule. A `RoomType` can have multiple `RatePolicy`
entries covering different periods (e.g., peak season vs. off-peak).

```java
public record RatePolicy(
    LocalDate validFrom,
    LocalDate validTo,
    BigDecimal pricePerNight,
    String currencyCode,              // ISO 4217, e.g. "USD", "BDT"
    DiscountPolicy discountPolicy     // optional — null means no discount
) {}
```

**Validation rules (service layer):**
- `validTo` must be strictly after `validFrom`
- `pricePerNight` must be > 0
- `currencyCode` — exactly 3 uppercase characters
- No overlapping date ranges within the same `RoomType`

See [ADR-001](decisions/ADR-001-rate-policy.md) for why this is a separate class.

---

### `DiscountPolicy`

An optional discount applied on top of a `RatePolicy`.

```java
public record DiscountPolicy(
    DiscountType type,
    BigDecimal value,      // percentage (20.0) or fixed amount (10.00)
    String reason          // e.g. "Early Bird", "Weekend Special"
) {}
```

**Validation rules (service layer):**
- `value` must be > 0
- For `PERCENTAGE` — value must be between 0 and 100 exclusive
- For `FIXED` — discount must not exceed `pricePerNight`

**Effective price calculation:**
- `PERCENTAGE`: `pricePerNight * (1 - value / 100)`
- `FIXED`: `pricePerNight - value`

---

### `Address`

A structured address held by `Hotel`. References `City` by ID only —
no embedded object.

```java
public record Address(
    String street,
    String area,        // optional — neighborhood or district, e.g. "Gulshan"
    String zipCode,
    UUID cityId
) {}
```

**Validation rules (service layer):**
- `street` — must not be blank
- `zipCode` — must not be blank
- `cityId` — must reference an existing `City`
- `area` — optional, can be null

---

### `PhoneNumber`

A structured phone number held by `Hotel`.

```java
public record PhoneNumber(
    String countryCode,    // e.g. "+880"
    String number          // e.g. "1712345678"
) {}
```

**Validation rules (service layer):**
- `countryCode` — starts with `+` followed by digits
- `number` — digits only, must not be blank

---

### `Coordinates`

A geographic coordinate pair. Used by `City` and potentially by
`RecommendationService` for proximity-based suggestions.

```java
public record Coordinates(
    double latitude,
    double longitude
) {}
```

---

### `ReviewRating`

A structured rating broken into categories. All scores are on a 1-10 scale.
The overall hotel rating is derived as the average of all five category
averages across all reviews for that hotel.

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

## Query / Result Records

### `CitySearchQuery`

Input to `HotelSearchService.searchByCity()`.

```java
public record CitySearchQuery(
    UUID userId,                           // nullable — null means anonymous search, not recorded
    UUID cityId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guestCount,
    List<Amenity> amenities,           // optional — empty list means no filter
    List<RoomCategory> categories,     // optional — empty list means no filter
    List<BedType> bedTypes             // optional — empty list means no filter
) {}
```

### `HotelSearchQuery`

Input to `HotelSearchService.searchByHotel()`.

```java
public record HotelSearchQuery(
    UUID hotelId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guestCount
) {}
```

### `HotelSummary`

A lightweight projection returned in search results. Not the full `Hotel`
entity — contains only what is needed to render a search results page.

```java
public record HotelSummary(
    UUID hotelId,
    String name,
    Address address,
    double rating,
    BigDecimal startingFromPrice
) {}
```

### `SearchResult`

The top-level response from `HotelSearchService`.

```java
public record SearchResult(
    List<HotelSummary> hotels,
    List<HotelSummary> suggestions,
    int page,
    int size,
    long totalResults
) {}
```

---

### `AddHotelRequest`

Input to `HotelService.addHotel()`. System-managed fields excluded —
`id`, `rating` (defaults to 0.0), `status` (defaults to PENDING),
and `roomTypes` (added later via `RoomTypeService`).

```java
public record AddHotelRequest(
    String name,
    Address address,
    String description,
    PhoneNumber phoneNumber,
    List<Amenity> amenities,
    UUID ownerId
) {}
```

### `EditHotelRequest`

Input to `HotelService.editHotel()`. All fields optional —
at least one must be present. `ownerId` not editable via this request.

```java
public record EditHotelRequest(
    Optional<String> name,
    Optional<Address> address,
    Optional<String> description,
    Optional<PhoneNumber> phoneNumber,
    Optional<List<Amenity>> amenities
) {}
```

---

### `AddRoomTypeRequest`

Input to `RoomTypeService.addRoomType()`. System-managed fields excluded —
`id`, `status` (defaults to `ACTIVE`), `ratePolicies` (added separately).

```java
public record AddRoomTypeRequest(
    String name,
    RoomCategory category,
    List<BedType> bedTypes,
    int totalRooms,
    int capacity,
    List<String> imageUrls
) {}
```

### `EditRoomTypeRequest`

Input to `RoomTypeService.editRoomType()`. All fields optional —
at least one must be present.

```java
public record EditRoomTypeRequest(
    Optional<String> name,
    Optional<RoomCategory> category,
    Optional<List<BedType>> bedTypes,
    Optional<Integer> totalRooms,
    Optional<Integer> capacity,
    Optional<List<String>> imageUrls
) {}
```

### `AddRatePolicyRequest`

Input to `RoomTypeService.addRatePolicy()`.

```java
public record AddRatePolicyRequest(
    LocalDate validFrom,
    LocalDate validTo,
    BigDecimal pricePerNight,
    String currencyCode,
    DiscountPolicy discountPolicy    // optional — null means no discount
) {}
```

### `EditRatePolicyRequest`

Input to `RoomTypeService.editRatePolicy()`. All fields optional —
at least one must be present.

```java
public record EditRatePolicyRequest(
    Optional<LocalDate> validFrom,
    Optional<LocalDate> validTo,
    Optional<BigDecimal> pricePerNight,
    Optional<String> currencyCode,
    Optional<DiscountPolicy> discountPolicy
) {}
```

---

### `CreateBookingRequest`

Input to `BookingService.createBooking()`.

```java
public record CreateBookingRequest(
    UUID hotelId,
    UUID roomTypeId,
    UUID userId,
    int guestCount,
    LocalDate checkIn,
    LocalDate checkOut
) {}
```

### `EditBookingRequest`

Input to `BookingService.editBooking()`. All fields optional —
at least one must be present. If dates are changed, both `checkIn`
and `checkOut` must be provided together.

```java
public record EditBookingRequest(
    Optional<LocalDate> checkIn,
    Optional<LocalDate> checkOut,
    Optional<Integer> guestCount
) {}
```

### `BookingSummary`

A lightweight projection returned in booking list responses.

```java
public record BookingSummary(
    UUID bookingId,
    String hotelName,
    String roomTypeName,
    LocalDate checkIn,
    LocalDate checkOut,
    BookingStatus status,
    BigDecimal totalPrice,
    String currencyCode
) {}
```

---

### `AddDestinationRequest`

Input to `DestinationService.addDestination()`. System-managed fields
excluded — `id`, `searchCount` (defaults to 0), `bookingCount` (defaults
to 0), `status` (defaults to `ACTIVE`), `createdAt`, `updatedAt`.

```java
public record AddDestinationRequest(
    String name,
    String description,
    List<String> imageUrls
) {}
```

### `EditDestinationRequest`

Input to `DestinationService.editDestination()`. All fields optional —
at least one must be present.

```java
public record EditDestinationRequest(
    Optional<String> name,
    Optional<String> description,
    Optional<List<String>> imageUrls
) {}
```

---

### `CreateReviewRequest`

Input to `ReviewService.writeReview()`.

```java
public record CreateReviewRequest(
    UUID bookingId,
    UUID userId,
    UUID hotelId,
    ReviewRating rating,
    String comment
) {}
```

### `EditReviewRequest`

Input to `ReviewService.editReview()`. At least one field must be present.

```java
public record EditReviewRequest(
    Optional<ReviewRating> rating,
    Optional<String> comment
) {}
```

---

### `RoomTypeAvailability`

A result record returned by `HotelManagementService` availability queries.

```java
public record RoomTypeAvailability(
    UUID roomTypeId,
    String roomTypeName,
    int totalRooms,
    int bookedRooms,
    int availableRooms,
    AvailabilityStatus status
) {}
```

### `Revenue`

A result record returned by `HotelManagementService.getRevenue()`.
Revenue is broken down per currency — not converted to a single currency.

```java
public record Revenue(
    UUID hotelId,
    RevenuePeriod period,
    Map<String, BigDecimal> revenuePerCurrency,  // e.g. {"USD": 1500.00, "BDT": 85000.00}
    LocalDateTime from,
    LocalDateTime to
) {}
```

### `OccupancyRate`

A result record returned by `HotelManagementService.getOccupancyRate()`.

```java
public record OccupancyRate(
    UUID hotelId,
    double rate,           // 0.0 to 1.0 — e.g. 0.75 means 75% occupied
    int totalRooms,
    int occupiedRooms,
    LocalDate asOf
) {}
```

---

## Invariants

- A `City` must reference an existing `Country`
- A `Hotel` address must reference an existing `City`
- A `Hotel` must reference an existing `User` with `HOTEL_OWNER` role via `ownerId`
- Only `ACTIVE` hotels appear in search results
- Only `ACTIVE` room types are included in search results
- `Hotel` rating is always derived from reviews — never set manually
- A `RatePolicy` must not have overlapping date ranges within the same `RoomType`
- For `PERCENTAGE` discount — value must be between 0 and 100 exclusive
- For `FIXED` discount — discount must not exceed `pricePerNight`
- A booking's `checkOut` must be strictly after `checkIn`
- `guestCount` must be ≤ the room type's `capacity`
- A room type cannot be double-booked: concurrent bookings cannot exceed `totalRooms`
- `totalPrice` is snapshotted at booking creation — never recalculated after
- `cancelledAt` and `cancellationReason` are only populated when status is `CANCELLED`
- A review can only be submitted for a `COMPLETED` booking
- One review per `bookingId` — uniqueness enforced on `bookingId`
- A guest may review the same hotel multiple times if they have multiple completed bookings
- All `ReviewRating` fields must be between 1 and 10 inclusive
- Only `ACTIVE` reviews are returned in public-facing queries
- Admin uses `deactivateReview` to hide reviews — hard deletes are not permitted
- `popularityScore` = `searchCount` + (`bookingCount` * 2)
- Only `ACTIVE` destinations appear in `getPopularDestinations`
- `OccupancyRate.rate` is always between 0.0 and 1.0 inclusive