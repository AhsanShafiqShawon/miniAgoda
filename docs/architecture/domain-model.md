# Domain Model

## Classification Guide

### Entity vs Value Object

When modeling a class, ask:

> **"Do I need to distinguish this object from another identical one?"**
> - **YES** → Entity (has a unique `UUID id`)
> - **NO** → Value Object (defined purely by its data that's why it should be immutable by design in almost all cases)

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
 ├── amenities → List<Amenity>
 └── List<RoomType>
       ├── category → RoomCategory
       ├── bedTypes → List<BedType>
       ├── capacity: int
       ├── totalRooms: int
       ├── imageUrls: List<String>
       └── List<RatePolicy>
             ├── pricePerNight: BigDecimal
             ├── currencyCode: String
             └── discountPolicy → DiscountPolicy (optional)
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
    List<RoomType> roomTypes
) {}
```

**Validation rules (service layer):**
- `name` — must not be blank
- `address` — must not be null
- `phoneNumber` — must not be null
- `status` — must not be null, defaults to `PENDING` on creation

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

## Query / Result Records

### `CitySearchQuery`

Input to `HotelSearchService.searchByCity()`.

```java
public record CitySearchQuery(
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

## Invariants

- A `City` must reference an existing `Country`
- A `Hotel` address must reference an existing `City`
- Only `ACTIVE` hotels appear in search results
- `Hotel` rating is always derived from reviews — never set manually
- A `RatePolicy` must not have overlapping date ranges within the same `RoomType`
- For `PERCENTAGE` discount — value must be between 0 and 100 exclusive
- For `FIXED` discount — discount must not exceed `pricePerNight`