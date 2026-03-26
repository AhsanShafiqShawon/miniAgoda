# API Contract: Availability

## Overview

`AvailabilityService` is responsible for tracking room availability,
answering availability queries for search and hotel management, and
updating inventory when bookings are created or cancelled.

It is NOT responsible for booking validation (that's `BookingService`),
pricing (that's `RatePolicy`), or physical room assignment (deferred —
see ADR-006).

See [ADR-007](../architecture/decisions/ADR-007-availability-service.md)
for the rationale behind this design.

## Collaborators

```java
@Service
public class AvailabilityService {
    private final AvailabilityRepository availabilityRepository;
}
```

## Inventory Initialization

`AvailabilityRepository` is initialized and maintained by `RoomTypeService`:

```java
// Inside RoomTypeService.addRoomType()
availabilityRepository.initializeInventory(roomType.id(), roomType.totalRooms());

// Inside RoomTypeService.deactivateRoomType()
availabilityRepository.removeInventory(roomTypeId);
```

---

## Public Methods

### `isAvailable(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int guests, int rooms)`

Checks if the requested number of rooms of a given type are available
for the requested date range and guest count.

```java
boolean isAvailable(UUID roomTypeId, LocalDate checkIn,
                    LocalDate checkOut, int guests, int rooms);
```

**Behavior:**
1. Look up room type — throw `ResourceNotFoundException` if not found
2. Check `guests` ≤ `roomType.capacity()`
3. Count booked rooms for the date range via `AvailabilityRepository`
4. Return `true` if `(totalRooms - bookedRooms) >= rooms`

---

### `getAvailableRoomTypes(UUID hotelId, LocalDate checkIn, LocalDate checkOut, int guests)`

Returns all available room types for a hotel for the requested date
range and guest count. Used by `HotelSearchService` — one call per
hotel instead of per-room-type checks.

```java
List<RoomType> getAvailableRoomTypes(UUID hotelId, LocalDate checkIn,
                                      LocalDate checkOut, int guests);
```

**Behavior:**
1. Fetch all `ACTIVE` room types for the hotel
2. Filter by `guests` ≤ `roomType.capacity()`
3. For each room type, check availability via `AvailabilityRepository`
4. Return only room types with at least one room available

---

### `getAvailabilityByRoomType(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut)`

Returns detailed availability data for a specific room type.

```java
RoomTypeAvailability getAvailabilityByRoomType(UUID roomTypeId,
                                                LocalDate checkIn,
                                                LocalDate checkOut);
```

---

## Internal Methods

### `blockRooms(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms)`

Reduces available room count for the date range. Called by `BookingService`
after a booking is created. Not exposed as public API.

```java
void blockRooms(UUID roomTypeId, LocalDate checkIn,
                LocalDate checkOut, int rooms);
```

---

### `releaseRooms(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms)`

Restores available room count for the date range. Called by `BookingService`
when a booking is cancelled or edited. Not exposed as public API.

```java
void releaseRooms(UUID roomTypeId, LocalDate checkIn,
                  LocalDate checkOut, int rooms);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Room type not found | `ResourceNotFoundException` |
| Hotel not found | `ResourceNotFoundException` |
| `rooms` < 1 | `InvalidAvailabilityRequestException` |
| `checkOut` ≤ `checkIn` | `InvalidAvailabilityRequestException` |
| `guests` < 1 | `InvalidAvailabilityRequestException` |