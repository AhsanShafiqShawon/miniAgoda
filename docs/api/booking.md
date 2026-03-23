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
    private final InventoryRepository inventoryRepository;
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
4. Acquire write lock on `InventoryRepository`
5. Recheck availability inside lock (TOCTOU-safe)
6. If available — snapshot `totalPrice` from applicable `RatePolicy` + `DiscountPolicy`
7. Create `Booking` with status `CONFIRMED`, set `createdAt` and `updatedAt`
8. Update `InventoryRepository`
9. Release lock and return `Booking`

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
4. Update `InventoryRepository` — release the held rooms back

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
5. Acquire write lock on `InventoryRepository`
6. Release current booking's inventory
7. Recheck availability for new dates/guest count
8. If available — update booking, re-snapshot `totalPrice`, update `updatedAt`
9. If not available — restore original inventory, throw `RoomNotAvailableException`
10. Release lock and return updated `Booking`

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