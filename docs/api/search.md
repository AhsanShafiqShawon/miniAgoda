# API Contract: Hotel Search

## Overview

`HotelSearchService` is responsible for searching available hotels and room
types matching a given query. It is NOT responsible for storing search history,
managing popular destinations, creating bookings, or any user-specific logic.

## Collaborators

```java
@Service
public class HotelSearchService {
    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final RecommendationService recommendationService;
}
```

## Methods

### `searchByCity(CitySearchQuery query, int page, int size)`

Searches for available hotels and room types in a given city.

```java
SearchResult searchByCity(CitySearchQuery query, int page, int size);
```

### `searchByHotel(HotelSearchQuery query, int page, int size)`

Drill-down search for available room types within a specific hotel.

```java
SearchResult searchByHotel(HotelSearchQuery query, int page, int size);
```

---

## Input Types

### `CitySearchQuery`

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

```java
public record HotelSearchQuery(
    UUID hotelId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guestCount
) {}
```

**Validation rules (applied at service entry — fail fast):**
- `checkOut` must be strictly after `checkIn`
- `guestCount` must be ≥ 1
- `cityId` / `hotelId` must exist — validated before any further processing

---

## Output Types

### `SearchResult`

```java
public record SearchResult(
    List<HotelSummary> hotels,
    List<HotelSummary> suggestions,
    int page,
    int size,
    long totalResults
) {}
```

- `hotels` — matching results for the query
- `suggestions` — populated only when `hotels` is empty; contains alternative
  hotels in the same city with relaxed criteria (different dates or capacity)
- `totalResults` — total count across all pages, used for pagination UI

### `HotelSummary`

A lightweight projection — not the full `Hotel` entity. Contains only what is
needed to render a search results page.

```java
public record HotelSummary(
    UUID hotelId,
    String name,
    Address address,
    double rating,
    BigDecimal startingFromPrice
) {}
```

- `startingFromPrice` — the lowest available room type price for the queried
  date range and guest count, after any applicable discount

---

## Internal Logic

Both `searchByCity` and `searchByHotel` follow the same internal steps:

1. **Validate query** — fail fast before any repository calls
2. **Record search asynchronously** — if `query.userId() != null`, call
   `searchHistoryService.recordSearch()` asynchronously via `@Async`.
   Never blocks the search response.
3. **Fetch hotels** — by `cityId` or `hotelId` via `HotelRepository`
4. **Filter by status** — exclude any hotel where `status != ACTIVE`
5. **Filter by amenities** — exclude hotels missing any requested `Amenity`
   (only applies to `searchByCity`)
6. **Iterate room types** — room types are fetched as part of the `Hotel` aggregate
7. **Filter by room type status** — exclude room types where `status != ACTIVE`
8. **Filter by category** — exclude room types not matching requested `RoomCategory`
   (only applies to `searchByCity`)
9. **Filter by bed type** — exclude room types not matching requested `BedType`
   (only applies to `searchByCity`)
10. **Filter by capacity** — exclude room types where `capacity < guestCount`
11. **Check availability** — via `InventoryRepository.countAvailableRooms()`
12. **Resolve rate** — find applicable `RatePolicy` for the requested date range
13. **Apply discount** — calculate effective price from `DiscountPolicy` if present
14. **Build `HotelSummary`** — assemble with `startingFromPrice` (lowest effective rate)
15. **Handle insufficient results** — if `hotels.size() < recommendation-threshold`,
    call `RecommendationService` for suggestions. Date relaxation is tried first;
    if still insufficient, guest count relaxation is tried. Already-found hotels
    are excluded from suggestions via `excludeHotelIds`.
16. **Sort results** — rating descending, price ascending as tiebreaker
17. **Apply pagination** — `page` and `size`
18. **Return `SearchResult`**

---

## Sorting

Sorting is internal — callers cannot override sort order.

| Field | Direction |
|---|---|
| Rating | Descending (best rated first) |
| Starting price | Ascending (cheapest first, as tiebreaker) |

---

## Configurable Properties

```properties
# Minimum results before recommendations are triggered
miniagoda.search.recommendation-threshold=5

# Number of days to expand date range for relaxed date recommendations
miniagoda.search.recommendation-flex-days=3

# Number of guests to reduce for relaxed guest count recommendations
miniagoda.search.recommendation-flex-guests=2
```

---

## Error Cases

| Condition | Exception |
|---|---|
| `checkOut` ≤ `checkIn` | `InvalidSearchQueryException` |
| `guestCount` < 1 | `InvalidSearchQueryException` |
| `cityId` not found | `ResourceNotFoundException` |
| `hotelId` not found | `ResourceNotFoundException` |
| No results found | No exception — returns empty `hotels`, populated `suggestions` |