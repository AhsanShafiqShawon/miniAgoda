# API Contract: Booking

## Overview

`BookingService` is responsible for guest-facing booking operations —
creating, editing, cancelling, and querying bookings. It is NOT responsible
for checking availability (that's `InventoryRepository`), processing payments
(future `PaymentService`), or hotel management operations (that's
`HotelManagementService`).

## Collaborators

```java
@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final AvailabilityService availabilityService;
    private final HotelRepository hotelRepository;
}
```

## Status Lifecycle

```
CONFIRMED → COMPLETED   (automatic — triggered after checkOut date passes)
          → CANCELLED   (triggered by cancelBooking())
```

Bookings are confirmed instantly on creation — no PENDING state.
COMPLETED transition is handled by a scheduled job, not a public method.

---

## Methods

### `createBooking(CreateBookingRequest request)`

Creates a new booking after verifying availability and capacity.

```java
Booking createBooking(CreateBookingRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Look up hotel and room type — throw `ResourceNotFoundException` if not found
3. Check `guestCount` ≤ `roomType.capacity()` — throw `InvalidBookingRequestException` if exceeded
4. Check `rooms` ≥ 1 — throw `InvalidBookingRequestException` if invalid
5. Call `availabilityService.isAvailable()` — throw `RoomNotAvailableException` if not available
6. Generate `bookingGroupId` if not provided (new transaction) or use existing (multi-room booking)
7. Snapshot `totalPrice` from applicable `RatePolicy` + `DiscountPolicy`
8. Create `Booking` with status `CONFIRMED`, set `createdAt` and `updatedAt`
9. Call `availabilityService.blockRooms()` — update inventory
10. Persist and return `Booking`

---

### `cancelBooking(UUID bookingId)`

Cancels an existing booking.

```java
void cancelBooking(UUID bookingId);
```

**Behavior:**
1. Look up booking — throw `BookingNotFoundException` if not found
2. Check status — throw `InvalidBookingStateException` if already `CANCELLED` or `COMPLETED`
3. Update status to `CANCELLED`, set `cancelledAt` to now, set `updatedAt`
4. Call `availabilityService.releaseRooms()` — restore inventory

---

### `editBooking(UUID bookingId, EditBookingRequest request)`

Edits an existing booking's dates or guest count.
Internally treated as cancel + rebook — full availability recheck required.

```java
Booking editBooking(UUID bookingId, EditBookingRequest request);
```

**Behavior:**
1. Validate request — at least one field must be present
2. If dates provided — both `checkIn` and `checkOut` must be present together
3. Look up booking — throw `BookingNotFoundException` if not found
4. Check status — only `CONFIRMED` bookings can be edited
5. Call `availabilityService.releaseRooms()` — release current booking's inventory
6. Call `availabilityService.isAvailable()` — check availability for new dates/rooms
7. If available — update booking, re-snapshot `totalPrice`, update `updatedAt`
8. Call `availabilityService.blockRooms()` — block new inventory
9. If not available — call `availabilityService.blockRooms()` to restore original,
   throw `RoomNotAvailableException`
10. Return updated `Booking`

---

### `getBookingById(UUID bookingId)`

Returns a single booking by ID.

```java
Booking getBookingById(UUID bookingId);
```

---

### `getAllBookingsByUser(UUID userId, int page, int size)`

Returns all bookings for a specific user, paginated.

```java
List<BookingSummary> getAllBookingsByUser(UUID userId, int page, int size);
```

---

### `getMyBookingsByStatus(UUID userId, BookingStatus status, int page, int size)`

Returns a user's bookings filtered by status, paginated.

```java
List<BookingSummary> getMyBookingsByStatus(
    UUID userId, BookingStatus status, int page, int size);
```

---

### `getAllBookingsByStatus(BookingStatus status, int page, int size)`

Admin operation — returns all bookings in the system filtered by status.

```java
List<BookingSummary> getAllBookingsByStatus(
    BookingStatus status, int page, int size);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Hotel or room type not found | `ResourceNotFoundException` |
| Booking not found | `BookingNotFoundException` |
| `guestCount` > room capacity | `InvalidBookingRequestException` |
| No rooms available | `RoomNotAvailableException` |
| Edit/cancel on COMPLETED booking | `InvalidBookingStateException` |
| Edit/cancel on CANCELLED booking | `InvalidBookingStateException` |
| `EditBookingRequest` with no fields | `InvalidBookingRequestException` |
| Only one of checkIn/checkOut provided | `InvalidBookingRequestException` |