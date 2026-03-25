# API Contract: Destination

## Overview

`DestinationService` is responsible for managing curated destinations
and providing popular destination data. It is NOT responsible for managing
hotels within a destination (that's `HotelService`), search history (that's
`SearchHistoryService`), or any booking logic.

## Collaborators

```java
@Service
public class DestinationService {
    private final DestinationRepository destinationRepository;
}
```

## Popularity Score

```java
popularityScore = searchCount + (bookingCount * 2)
```

Bookings are weighted higher than searches — a booked destination is more
meaningful than a searched one. Score is recalculated implicitly as counts
are incremented asynchronously.

## How Counts Are Updated

- `searchCount` — incremented asynchronously by `SearchHistoryService.recordSearch()`
  when a city is searched
- `bookingCount` — incremented asynchronously by `BookingService.createBooking()`
  when a booking is confirmed in that city

---

## Methods

### `addDestination(UUID cityId, AddDestinationRequest request)`

Creates a new destination for a city. Status defaults to `ACTIVE`.
`searchCount` and `bookingCount` default to 0.

```java
Destination addDestination(UUID cityId, AddDestinationRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Look up city — throw `ResourceNotFoundException` if not found
3. Create `Destination` with status `ACTIVE`, counts at 0
4. Set `createdAt`, persist and return

---

### `editDestination(UUID id, EditDestinationRequest request)`

Partially updates an existing destination.

```java
Destination editDestination(UUID id, EditDestinationRequest request);
```

**Behavior:**
1. Validate request — at least one field must be present
2. Look up destination — throw `ResourceNotFoundException` if not found
3. Apply present fields, set `updatedAt`
4. Persist and return updated `Destination`

---

### `activateDestination(UUID id)`

Sets destination status to `ACTIVE`. Destination becomes publicly visible.

```java
void activateDestination(UUID id);
```

---

### `deactivateDestination(UUID id)`

Sets destination status to `INACTIVE`. Destination is hidden from public.

```java
void deactivateDestination(UUID id);
```

---

### `getDestinationById(UUID id)`

Returns a single destination by ID.

```java
Destination getDestinationById(UUID id);
```

---

### `getAllDestinations(int page, int size)`

Admin-only operation. Returns all destinations regardless of status, paginated.

```java
List<Destination> getAllDestinations(int page, int size);
```

---

### `getAllDestinationsByCity(UUID cityId, int page, int size)`

Returns all `ACTIVE` destinations for a specific city, paginated.

```java
List<Destination> getAllDestinationsByCity(UUID cityId, int page, int size);
```

---

### `getPopularDestinations(int page, int size)`

Returns `ACTIVE` destinations sorted by `popularityScore` descending, paginated.

```java
List<Destination> getPopularDestinations(int page, int size);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Destination not found | `ResourceNotFoundException` |
| City not found | `ResourceNotFoundException` |
| `EditDestinationRequest` with no fields | `InvalidDestinationRequestException` |
| Activating already `ACTIVE` destination | `InvalidDestinationStateException` |
| Deactivating already `INACTIVE` destination | `InvalidDestinationStateException` |