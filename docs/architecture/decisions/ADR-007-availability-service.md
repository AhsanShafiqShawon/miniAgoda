# ADR-007: AvailabilityService as a Dedicated Service with AvailabilityRepository

## Status
Accepted

## Context

Room availability needs to be checked during search and updated during
booking creation, editing, and cancellation. The question is where this
logic lives and how it is persisted.

Three options were considered:

1. **Inside `BookingRepository`** — availability derived by counting
   overlapping bookings directly. Simple but couples search to booking
   internals (already rejected in [ADR-004](ADR-004-inventory-repository.md)).

2. **Inside `AvailabilityService` with in-memory state** — a `Map<UUID,
   List<RoomType>>` field backing availability checks. Simple for the
   current phase but not database-ready and not thread-safe at scale.

3. **`AvailabilityService` backed by `AvailabilityRepository`** — a
   dedicated service with a proper repository owning availability
   persistence. Thread safety handled at the repository level.

## Decision

Introduce a dedicated `AvailabilityService` backed by
`AvailabilityRepository`. The repository owns all availability state —
no in-memory maps in the service layer.

```java
@Service
public class AvailabilityService {
    private final AvailabilityRepository availabilityRepository;

    // Public API
    boolean isAvailable(UUID roomTypeId, LocalDate checkIn,
                        LocalDate checkOut, int guests, int rooms);

    List<RoomType> getAvailableRoomTypes(UUID hotelId, LocalDate checkIn,
                                          LocalDate checkOut, int guests);

    RoomTypeAvailability getAvailabilityByRoomType(UUID roomTypeId,
                                                    LocalDate checkIn,
                                                    LocalDate checkOut);

    // Internal — called by BookingService only
    void blockRooms(UUID roomTypeId, LocalDate checkIn,
                    LocalDate checkOut, int rooms);

    void releaseRooms(UUID roomTypeId, LocalDate checkIn,
                      LocalDate checkOut, int rooms);
}
```

`blockRooms` and `releaseRooms` are internal — not exposed as public API.
Only `BookingService` calls them, preventing inventory updates without
a corresponding booking.

`RoomTypeService` initializes inventory when a room type is added and
removes it when a room type is deactivated:

```java
// Inside RoomTypeService.addRoomType()
availabilityRepository.initializeInventory(roomType.id(), roomType.totalRooms());

// Inside RoomTypeService.deactivateRoomType()
availabilityRepository.removeInventory(roomTypeId);
```

`HotelSearchService` uses `getAvailableRoomTypes` — one call per hotel
returns all available room types at once, rather than checking each
room type individually.

## Consequences

**Positive:**
- Clean separation — availability logic has a single, dedicated home
- Thread safety handled at `AvailabilityRepository` level — consistent
  with [ADR-003](ADR-003-concurrency.md)
- `HotelSearchService` makes one availability call per hotel — more
  efficient than per-room-type checks
- `blockRooms` / `releaseRooms` as internal methods prevents inventory
  updates without a corresponding booking
- Database-ready — no in-memory state to migrate
- Aligns with future microservices split — Availability becomes its
  own service boundary

**Negative:**
- `RoomTypeService` now has a dependency on `AvailabilityRepository` —
  slight coupling between two services
- One additional abstraction to implement and maintain

## Alternatives Considered

- **In-memory `Map` in `AvailabilityService`**: Simpler short-term but
  not thread-safe at scale and requires migration to a database later.
  Rejected in favour of repository-backed approach.
- **Query `BookingRepository` directly**: Already rejected in [ADR-004](ADR-004-inventory-repository.md) —
  couples search to booking internals.

## Related Decisions

- [ADR-003](ADR-003-concurrency.md) — thread safety at repository level
- [ADR-004](ADR-004-inventory-repository.md) — availability separate
  from booking (superseded by this ADR — `InventoryRepository` renamed
  to `AvailabilityRepository`)