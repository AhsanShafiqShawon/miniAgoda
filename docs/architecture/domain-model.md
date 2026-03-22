# Domain Model

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
       ├── capacity: int
       ├── totalRooms: int
       └── List<RatePolicy>     (date-range → price per night)

Booking
 ├── roomTypeId → RoomType
 ├── hotelId → Hotel
 ├── guestCount: int
 ├── checkIn: LocalDate
 └── checkOut: LocalDate
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

---

## Value Objects

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
    List<Amenity> amenities    // optional — empty list means no filter
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

- A booking's `checkOut` must be strictly after `checkIn`
- `guestCount` must be ≤ the room type's `capacity`
- A room type cannot be double-booked: concurrent bookings cannot exceed `totalRooms`
- A `RatePolicy` must not have overlapping date ranges within the same `RoomType`
- A `City` must reference an existing `Country`
- A `Hotel` address must reference an existing `City`
- Only `ACTIVE` hotels appear in search results
- `Hotel` rating is always derived from reviews — never set manually