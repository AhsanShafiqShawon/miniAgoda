# ADR-002: Availability Modeled Per Room Type

## Status
Accepted (revised — see revision note below)

## Context

When checking if a room is available for a given date range, we need to decide
what the unit of availability is. Two options:

1. **Per physical room** — each room has an ID; a booking is tied to room #101,
   #102, etc.
2. **Per room type** — we track how many rooms of type "Deluxe Double" are
   available, without assigning a specific physical room.

## Decision

Model availability at the **room type** level. `RoomType` has a `totalRooms`
count. Availability is checked via `InventoryRepository`, which tracks how
many rooms of a given type are available for a requested date range.

```java
int available = inventoryRepository.countAvailableRooms(
    hotelId, roomTypeId, checkIn, checkOut);
boolean isAvailable = available > 0;
```

`InventoryRepository` is the single source of truth for availability. It is
updated by `BookingService` when bookings are created or cancelled. See
[ADR-004](ADR-004-inventory-repository.md) for the rationale behind this separation.

## Consequences

**Positive:**
- Simpler query: one availability check instead of iterating individual room IDs
- No need to manage a physical room inventory at this stage
- `InventoryRepository` encapsulates availability logic — search never touches
  booking internals directly
- Mirrors how most OTA search engines work at the query layer

**Negative:**
- Cannot assign a specific room number to a booking (e.g., "you're in room 204")
- Physical room features (floor, view, accessible) can't be filtered — all
  rooms of a type are interchangeable

## Alternatives Considered

- **Per physical room with room inventory**: Necessary for a production system,
  but adds significant complexity (room assignment, room state management).
  Deferred to a later phase.
- **Query `BookingRepository` directly for availability**: Couples search to
  booking internals and makes availability logic harder to extend. Rejected in
  favour of `InventoryRepository` — see [ADR-004](decisions/ADR-004-inventory-repository.md).

---

## Revision Note

Originally written with availability computed by counting overlapping bookings
directly from `BookingRepository`. Revised after [ADR-004](decisions/ADR-004-inventory-repository.md) was accepted —
availability is now owned by `InventoryRepository`, keeping booking and
inventory concerns separate.
