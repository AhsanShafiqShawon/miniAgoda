# ADR-006: Defer RoomService and Physical Room Tracking

## Status
Accepted

## Context

Hotels have physical rooms (e.g. room #101, #102, #103). A natural question
is whether miniAgoda should track these individual rooms and introduce a
`RoomService` to manage them.

Physical room tracking would enable:
- Assigning a specific room number to a booking
- Filtering by room-specific features (floor, view, accessible)
- Managing per-room maintenance status

However, ADR-002 already established that availability is modeled at the
**room type** level — all rooms of a given type are interchangeable. There
is no current use case in miniAgoda that requires knowing which specific
physical room a guest is staying in.

## Decision

Defer `RoomService` and physical room tracking to a later phase.
Only `RoomTypeService` is introduced at this stage.

No `Room` entity, no `RoomRepository`, and no `RoomService` will be
created until a concrete use case requires it.

## Consequences

**Positive:**
- Keeps the domain model lean — no entity without a clear use case
- Consistent with ADR-002 — availability per room type, not per physical room
- Reduces implementation scope for the current phase
- `RoomTypeService` stays focused without physical room concerns mixed in

**Negative:**
- Cannot assign specific room numbers to bookings (e.g. "you're in room 204")
- Cannot filter by room-specific features (floor, view, accessible)
- Cannot track per-room maintenance status

## Future Phase

`RoomService` will be introduced when physical room assignment is needed.
At that point:
- A `Room` entity will be added with fields: `id`, `roomNumber`, `floor`,
  `view`, `isAccessible`, `status`, `roomTypeId`
- `RoomService` will manage room inventory
- `BookingService` will be updated to assign a specific room on booking

## Related Decisions

- [ADR-002](ADR-002-availability-per-room-type.md) — availability modeled
  per room type, not per physical room