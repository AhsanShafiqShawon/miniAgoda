# API Contract: Hotel

## Overview

`HotelService` is responsible for hotel data management — creating, editing,
activating, deactivating, and querying hotels. It is NOT responsible for
room type management (that's `RoomTypeService`), availability search (that's
`HotelSearchService`), or booking queries (that's `HotelManagementService`).

## Collaborators

```java
@Service
public class HotelService {
    private final HotelRepository hotelRepository;
}
```

---

## Methods

### `addHotel(AddHotelRequest request)`

Creates a new hotel. Status defaults to `PENDING`, rating defaults to `0.0`.

```java
Hotel addHotel(AddHotelRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Verify `ownerId` references a `User` with `HOTEL_OWNER` role
3. Verify `address.cityId` references an existing `City`
4. Create `Hotel` with status `PENDING` and rating `0.0`
5. Persist and return

---

### `editHotel(UUID id, EditHotelRequest request)`

Partially updates an existing hotel. `ownerId` is not editable.

```java
Hotel editHotel(UUID id, EditHotelRequest request);
```

**Behavior:**
1. Validate request — at least one field must be present
2. Look up hotel — throw `ResourceNotFoundException` if not found
3. Apply present fields, leave absent fields unchanged
4. Persist and return updated `Hotel`

---

### `activateHotel(UUID id)`

Sets hotel status to `ACTIVE`. Hotel becomes visible in search results.

```java
void activateHotel(UUID id);
```

---

### `deactivateHotel(UUID id)`

Sets hotel status to `INACTIVE`. Hotel is excluded from search results.

```java
void deactivateHotel(UUID id);
```

---

### `getHotelById(UUID id)`

Returns a single hotel by ID regardless of status.

```java
Hotel getHotelById(UUID id);
```

---

### `getAllHotels(int page, int size)`

Admin-only operation. Returns all hotels regardless of status, paginated.

```java
List<HotelSummary> getAllHotels(int page, int size);
```

---

### `getHotelsByCity(UUID cityId, int page, int size)`

Returns `ACTIVE` hotels in a given city, paginated.
`INACTIVE` and `PENDING` hotels are automatically excluded.

```java
List<HotelSummary> getHotelsByCity(UUID cityId, int page, int size);
```

---

### `getHotelsByOwner(UUID ownerId, int page, int size)`

Returns all hotels owned by a specific user, regardless of status, paginated.

```java
List<HotelSummary> getHotelsByOwner(UUID ownerId, int page, int size);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Hotel not found | `ResourceNotFoundException` |
| City not found | `ResourceNotFoundException` |
| `ownerId` not found or not `HOTEL_OWNER` | `InvalidHotelRequestException` |
| `EditHotelRequest` with no fields | `InvalidHotelRequestException` |
| Activating already `ACTIVE` hotel | `InvalidHotelStateException` |
| Deactivating already `INACTIVE` hotel | `InvalidHotelStateException` |