# ADR-002: Availability Modeled Per Room Type

## Status
Accepted

## Context

When checking if a room is available for a given date range, we need to decide what the unit of availability is. Two options:

1. **Per physical room** — each room has an ID; a booking is tied to room #101, #102, etc.
2. **Per room type** — we track how many rooms of type "Deluxe Double" are available, without assigning a specific physical room.

## Decision

Model availability at the **room type** level. `RoomType` has a `totalRooms` count. Availability is computed by counting active bookings for that room type in the requested date window and comparing against `totalRooms`.

```java
int booked = bookingRepository.countOverlapping(hotelId, roomTypeId, checkIn, checkOut);
boolean available = booked < roomType.totalRooms();
```

## Consequences

**Positive:**
- Simpler query: one count check instead of iterating individual room IDs
- No need to manage a physical room inventory at this stage
- Easier to reason about concurrency — one atomic counter per room type
- Mirrors how most OTA search engines work at the query layer

**Negative:**
- Cannot assign a specific room number to a booking (e.g., "you're in room 204")
- Physical room features (floor, view, accessible) can't be filtered — all rooms of a type are interchangeable

## Alternatives Considered

- **Per physical room with room inventory**: Necessary for a production system, but adds significant complexity (room assignment, room state management). Deferred to a later phase.