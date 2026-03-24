# API Contract: RoomType

## Overview

`RoomTypeService` is responsible for managing room types within a hotel
and their rate policies. It is NOT responsible for availability checking
(that's `InventoryRepository`), pricing resolution during search (that's
`HotelSearchService`), or physical room tracking (deferred — see [ADR-006](ADR-006-defer-room-service.md)).

## Collaborators

```java
@Service
public class RoomTypeService {
    private final HotelRepository hotelRepository;
}
```

---

## Methods

### `addRoomType(UUID hotelId, AddRoomTypeRequest request)`

Adds a new room type to a hotel. Status defaults to `ACTIVE`.

```java
RoomType addRoomType(UUID hotelId, AddRoomTypeRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Look up hotel — throw `ResourceNotFoundException` if not found
3. Create `RoomType` with status `ACTIVE` and empty `ratePolicies`
4. Add to hotel's room types, persist and return

---

### `editRoomType(UUID id, EditRoomTypeRequest request)`

Partially updates an existing room type.

```java
RoomType editRoomType(UUID id, EditRoomTypeRequest request);
```

**Behavior:**
1. Validate request — at least one field must be present
2. Look up room type — throw `ResourceNotFoundException` if not found
3. Apply present fields, leave absent fields unchanged
4. Persist and return updated `RoomType`

---

### `getRoomTypeById(UUID id)`

Returns a single room type by ID.

```java
RoomType getRoomTypeById(UUID id);
```

---

### `getAllRoomTypes(int page, int size)`

Admin-only operation. Returns all room types regardless of status, paginated.

```java
List<RoomType> getAllRoomTypes(int page, int size);
```

---

### `getRoomTypesByHotel(UUID hotelId, int page, int size)`

Returns all room types for a specific hotel, paginated.

```java
List<RoomType> getRoomTypesByHotel(UUID hotelId, int page, int size);
```

---

### `activateRoomType(UUID id)`

Sets room type status to `ACTIVE`. Room type becomes bookable.

```java
void activateRoomType(UUID id);
```

---

### `deactivateRoomType(UUID id)`

Sets room type status to `INACTIVE`. Room type is excluded from search
and cannot be booked.

```java
void deactivateRoomType(UUID id);
```

---

### `addRatePolicy(UUID roomTypeId, AddRatePolicyRequest request)`

Adds a new rate policy to a room type. Validates no overlapping date ranges.

```java
RoomType addRatePolicy(UUID roomTypeId, AddRatePolicyRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Look up room type — throw `ResourceNotFoundException` if not found
3. Check for overlapping date ranges with existing policies —
   throw `OverlappingRatePolicyException` if conflict found
4. Add policy, persist and return updated `RoomType`

---

### `editRatePolicy(UUID roomTypeId, UUID ratePolicyId, EditRatePolicyRequest request)`

Partially updates an existing rate policy. Re-validates overlap after edit.

```java
RoomType editRatePolicy(UUID roomTypeId, UUID ratePolicyId,
                        EditRatePolicyRequest request);
```

**Behavior:**
1. Validate request — at least one field must be present
2. Look up room type and rate policy — throw `ResourceNotFoundException` if not found
3. Apply present fields
4. Re-validate no overlapping date ranges — throw `OverlappingRatePolicyException` if conflict
5. Persist and return updated `RoomType`

---

### `removeRatePolicy(UUID roomTypeId, UUID ratePolicyId)`

Removes a rate policy from a room type.

```java
void removeRatePolicy(UUID roomTypeId, UUID ratePolicyId);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Hotel or room type not found | `ResourceNotFoundException` |
| Rate policy not found | `ResourceNotFoundException` |
| Overlapping rate policy date ranges | `OverlappingRatePolicyException` |
| `EditRoomTypeRequest` with no fields | `InvalidRoomTypeRequestException` |
| `EditRatePolicyRequest` with no fields | `InvalidRoomTypeRequestException` |
| Activating already `ACTIVE` room type | `InvalidRoomTypeStateException` |
| Deactivating already `INACTIVE` room type | `InvalidRoomTypeStateException` |
| `pricePerNight` ≤ 0 | `InvalidRoomTypeRequestException` |
| `capacity` < 1 | `InvalidRoomTypeRequestException` |
| `totalRooms` < 1 | `InvalidRoomTypeRequestException` |