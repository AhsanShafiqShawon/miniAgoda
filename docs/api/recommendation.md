# API Contract: Recommendation

## Overview

`RecommendationService` is responsible for suggesting alternative hotels
when search results are empty or below a configured threshold. It is NOT
responsible for personalized recommendations based on user history (that's
`SearchHistoryService`), popular destinations (that's `DestinationService`),
or any booking or pricing logic.

## Collaborators

```java
@Service
public class RecommendationService {
    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
}
```

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

## How HotelSearchService Uses RecommendationService

Recommendations are triggered when `hotels.size() < recommendation-threshold`.
Date relaxation is tried first — if still insufficient, guest count relaxation
is tried. Already-found hotels are always excluded from suggestions.

```java
if (hotels.size() < recommendationThreshold) {
    List<UUID> foundHotelIds = hotels.stream()
        .map(HotelSummary::hotelId)
        .toList();

    suggestions = recommendationService.getRecommendationByRelaxedDate(
        cityId, checkIn, checkOut, flexDays,
        guestCount, foundHotelIds, 0, 5);

    if (suggestions.isEmpty()) {
        suggestions = recommendationService.getRecommendationByRelaxedGuestCount(
            cityId, checkIn, checkOut, guestCount,
            flex, foundHotelIds, 0, 5);
    }
}
```

---

## Methods

### `getRecommendationByRelaxedDate(...)`

Searches for available hotels in the same city by expanding the date range
by ±`flexDays` around the original `checkIn` and `checkOut`. Returns hotels
not already in the current results.

```java
List<HotelSummary> getRecommendationByRelaxedDate(
    UUID cityId,
    LocalDate checkIn,
    LocalDate checkOut,
    int flexDays,
    int guestCount,
    List<UUID> excludeHotelIds,
    int page, int size);
```

**Behavior:**
1. Generate date variations: `checkIn ± flexDays`, `checkOut ± flexDays`
2. For each date variation, search available hotels in `cityId`
3. Filter by `guestCount` capacity
4. Exclude hotels in `excludeHotelIds`
5. Deduplicate results across date variations
6. Sort by rating descending, price ascending
7. Apply pagination and return

---

### `getRecommendationByRelaxedGuestCount(...)`

Searches for available hotels in the same city by reducing `guestCount`
by up to `flex`. Useful when no rooms accommodate the original guest count.

```java
List<HotelSummary> getRecommendationByRelaxedGuestCount(
    UUID cityId,
    LocalDate checkIn,
    LocalDate checkOut,
    int guestCount,
    int flex,
    List<UUID> excludeHotelIds,
    int page, int size);
```

**Behavior:**
1. Generate guest count variations: `guestCount-1` down to `guestCount-flex`
2. For each variation, search available hotels in `cityId`
3. Exclude hotels in `excludeHotelIds`
4. Deduplicate results across guest count variations
5. Sort by rating descending, price ascending
6. Apply pagination and return

---

## Error Cases

| Condition | Exception |
|---|---|
| `cityId` not found | `ResourceNotFoundException` |
| `flexDays` < 1 | `InvalidRecommendationRequestException` |
| `flex` < 1 | `InvalidRecommendationRequestException` |
| `guestCount` < 1 | `InvalidRecommendationRequestException` |
| No suggestions found | Returns empty list — no exception |