# ADR-005: RecommendationService for Empty Search Results

## Status
Accepted

## Context

When a search returns no results, returning an empty list silently is a poor
user experience. Users need guidance — either the dates are too restrictive,
the city has no available hotels, or the guest count is too high for available
room types.

The question is: should `HotelSearchService` handle fallback suggestions
itself, or delegate to a separate service?

## Decision

Introduce a dedicated `RecommendationService` that `HotelSearchService` calls
only when search results are empty.

```java
if (hotels.isEmpty()) {
    suggestions = recommendationService.suggestAlternatives(query);
}
```

For the MVP, suggestions are simple — alternative hotels in the same city with
relaxed criteria (nearby dates or higher capacity rooms). A full recommendation
engine is a future concern.

## Consequences

**Positive:**
- `HotelSearchService` stays focused on availability search — suggestion logic
  doesn't pollute it
- `RecommendationService` can evolve independently — ML-based recommendations,
  trending hotels, personalized suggestions can all be added later
- Clean separation that maps to a future microservice boundary
- Empty results never surface as a silent failure to the user

**Negative:**
- One additional collaborator in `HotelSearchService`
- `RecommendationService` needs its own design and implementation

## Alternatives Considered

- **Handle suggestions inside `HotelSearchService`**: Simpler but mixes two
  concerns in one class. Rejected because suggestion logic will grow in
  complexity over time.
- **Return empty results and handle fallback in the controller**: Pushes
  business logic into the wrong layer. Rejected.