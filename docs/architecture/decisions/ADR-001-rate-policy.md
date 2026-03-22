# ADR-001: RatePolicy as a Separate Class

## Status
Accepted

## Context

Rooms need to have prices. The simplest approach is to add a `pricePerNight` field directly onto `RoomType`. However, hotel pricing in the real world is date-dependent — peak season, holidays, and promotional windows all have different rates.

If we embed a single price in `RoomType`, any date-aware pricing logic bleeds into the entity itself or into the service layer with no clear home.

## Decision

Introduce a dedicated `RatePolicy` class that maps a date range (`validFrom`, `validTo`) to a `pricePerNight`. A `RoomType` holds a `List<RatePolicy>`.

```java
public record RatePolicy(
    LocalDate validFrom,
    LocalDate validTo,
    BigDecimal pricePerNight
) {}
```

`HotelSearchService` is responsible for finding the applicable `RatePolicy` for a given search date range.

## Consequences

**Positive:**
- `RoomType` stays clean — no pricing logic embedded in the entity
- Supports seasonal and promotional pricing without structural changes
- `RatePolicy` is an immutable record — thread-safe by design
- Easy to extend: discount policies, currency-specific rates, etc. can be added as new policy types

**Negative:**
- Slightly more indirection when looking up a room's price (need to search the policy list)
- Overlapping policies need to be validated — an invariant that must be enforced at construction or insertion time

## Alternatives Considered

- **Single `pricePerNight` on `RoomType`**: Simple but doesn't support date-based pricing at all. Rejected as it wouldn't survive a real-world use case.
- **Pricing table in a separate repository**: Over-engineered for the current scope. Can be introduced in the microservices phase.