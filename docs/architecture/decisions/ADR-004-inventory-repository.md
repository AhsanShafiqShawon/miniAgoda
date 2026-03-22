# ADR-004: Availability Checking via InventoryRepository, not BookingRepository

## Status
Accepted

## Context

When checking room availability during search, we need to answer: "how many
rooms of this type are available for this date range?" The data that informs
this answer is closely related to bookings — a room is unavailable if it has
an active booking for the requested period.

The question is: should `HotelSearchService` ask `BookingRepository` directly,
or should there be a dedicated `InventoryRepository`?

## Decision

Introduce a dedicated `InventoryRepository` that owns the question of
availability. `HotelSearchService` depends on `InventoryRepository`, not
`BookingRepository`.

```java
public interface InventoryRepository {
    int countAvailableRooms(UUID hotelId, UUID roomTypeId,
                            LocalDate checkIn, LocalDate checkOut);
}
```

`BookingService` updates inventory when bookings are created or cancelled.

## Consequences

**Positive:**
- `BookingRepository` stays focused on managing booking records only
- `InventoryRepository` can evolve independently — manual room blocks, channel
  allocations, and other reasons for unavailability can be added without
  touching booking logic
- Aligns with real OTA architecture where Inventory and Booking are separate
  services — this boundary will become a service split in the microservices phase
- `HotelSearchService` has no dependency on booking internals

**Negative:**
- One additional abstraction to implement and maintain
- `BookingService` must update both `BookingRepository` and `InventoryRepository`
  on create/cancel — two writes instead of one

## Alternatives Considered

- **Query `BookingRepository` directly from `HotelSearchService`**: Simpler
  short-term but couples search to booking internals. Rejected because it
  violates the future service boundary and makes inventory logic harder to extend.