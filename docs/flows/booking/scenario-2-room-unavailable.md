# Scenario 2: Booking — Room No Longer Available (TOCTOU Race)

**User:** Shawon (logged in)
**Action:** Tries to book the last available Deluxe King room
**Outcome:** Room was taken by another user between search and booking

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 2 |
|---|---|---|
| Rooms available at check | 7 | 0 (last room taken) |
| Write lock result | Available ✅ | Fully booked ❌ |
| Booking created | Yes | No |
| HTTP Status | 201 Created | 409 Conflict |
| Response time | ~423ms | ~180ms (no price calc, no inserts) |

---

## The Conversation

*(Network layers — Shawon → Browser → TLS → TCP/IP → Internet → CDN →
API Gateway → Load Balancer → Nginx → Tomcat → DispatcherServlet →
Spring Security Filter — identical to Scenario 1. Picking up at
BookingController.)*

---

**BookingController:** Deserializing request:
```java
CreateBookingRequest request = new CreateBookingRequest(
    hotelId:    UUID("hotel-001-uuid"),
    roomTypeId: UUID("rt-001-uuid"),
    userId:     UUID("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
    rooms:      1,
    guestCount: 2,
    checkIn:    LocalDate.of(2024, 12, 20),
    checkOut:   LocalDate.of(2024, 12, 25)
);
```

Calling BookingService.createBooking(request).

---

**BookingService:**

**Step 1 — Validate:** Valid ✅

**Step 2 — Look up hotel and room type:**
HotelRepository, fetch hotel-001-uuid and rt-001-uuid.

---

**HotelRepository:**
```sql
SELECT h.id, h.name, h.status,
       rt.id AS rt_id, rt.name AS rt_name,
       rt.total_rooms, rt.capacity, rt.status AS rt_status
FROM   hotels h
JOIN   room_types rt ON rt.hotel_id = h.id
WHERE  h.id  = 'hotel-001-uuid'
AND    rt.id = 'rt-001-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
hotel_status: ACTIVE
rt_status:    ACTIVE
capacity:     2
total_rooms:  10
```

---

**HotelRepository:** Found. Returning to BookingService.

---

**BookingService:**

**Step 3 — Validate capacity:**
```
guestCount: 2 ≤ capacity: 2 ✅
```

**Step 4 — Check availability inside write lock:**

AvailabilityService, acquire write lock and check availability.

---

**AvailabilityService:** Acquiring write lock.

```java
writeLock.lock();
```

Meanwhile — what happened between Shawon's search and now:
```
During Shawon's search:    7 rooms available
Just before Shawon's lock: Another user booked the last 7 rooms
                            (7 bookings created while Shawon was
                            deciding and filling the form)
Now:                        0 rooms available
```

Write lock acquired. Checking availability:

AvailabilityRepository, count booked rooms for rt-001-uuid Dec 20–25.

---

**AvailabilityRepository:**
```sql
SELECT COALESCE(SUM(rooms), 0) AS booked_rooms
FROM   bookings
WHERE  room_type_id = 'rt-001-uuid'
AND    status       IN ('CONFIRMED', 'COMPLETED')
AND    check_in     < '2024-12-25'
AND    check_out    > '2024-12-20';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(bookings table — 7 more bookings created after search)*
```
booked_rooms
------------
10

All 10 Deluxe King rooms booked for this period.
```

---

**AvailabilityRepository:** booked_rooms = 10. Returning to
AvailabilityService.

---

**AvailabilityService:** Checking:
```
totalRooms:  10
bookedRooms: 10
available:   10 - 10 = 0
requested:   1

0 < 1 ❌ — NO ROOMS AVAILABLE
```

Releasing write lock immediately:
```java
} finally {
    writeLock.unlock();
}
```

Returning false (not available) to BookingService.

---

**BookingService:** Room is not available.

No booking created. No price calculated. No emails sent.

Throwing RoomNotAvailableException:
```java
throw new RoomNotAvailableException(
    "No rooms of type Deluxe King are available at Grand Hyatt Bangkok " +
    "for December 20–25, 2024. " +
    "Please try different dates or another room type."
);
```

---

**GlobalExceptionHandler:** Caught RoomNotAvailableException.

```java
@ExceptionHandler(RoomNotAvailableException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleRoomNotAvailable(RoomNotAvailableException ex) {
    return new ErrorResponse(
        status:    409,
        error:     "Conflict",
        message:   ex.getMessage(),
        path:      "/api/v1/bookings",
        suggestion: "Try different dates or check other room types."
    );
}
```

---

**BookingController:** Serializing 409 response:

```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-6f7g8h9i-0j1k-2l3m-4n5o

{
  "status":     409,
  "error":      "Conflict",
  "message":    "No rooms of type Deluxe King are available at Grand Hyatt Bangkok for December 20–25, 2024.",
  "path":       "/api/v1/bookings",
  "suggestion": "Try different dates or check other room types."
}
```

Note: HTTP 409 Conflict — the request conflicts with the current
state of availability. Not 400 (the request itself is valid) and
not 500 (this is an expected business scenario).

---

*(Return path — identical structure to Scenario 1 return path.)*

---

**Browser:** Received 409. Rendering:

```
⚠️ Room No Longer Available

No rooms of type Deluxe King are available at
Grand Hyatt Bangkok for December 20–25, 2024.

This room may have been booked by another guest.

[Try Different Dates]   [See Other Room Types]   [Search Again]
```

---

**Shawon:** Oh no — someone else booked it while I was deciding.
Let me try the Chatrium hotel instead.

---

## Why This Can Happen

```
10:45:00 — Shawon's search returns: 7 Deluxe King rooms available
10:45:30 — Shawon reads results, decides on Grand Hyatt
10:46:00 — 7 other users book the remaining 7 rooms
10:46:30 — Shawon clicks "Book Now"
10:46:31 — AvailabilityService acquires write lock
10:46:31 — Availability check: 0 rooms available
10:46:31 — RoomNotAvailableException thrown
10:46:31 — 409 Conflict returned to Shawon
```

The write lock prevents double-booking but cannot prevent this
scenario — the lock only protects the check-then-book operation,
not the time between search and booking attempt.

This is the fundamental TOCTOU (Time-Of-Check-Time-Of-Use) challenge
in booking systems. The correct response is to surface the conflict
clearly and guide the user to alternatives.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Clicks Book Now — room was available in search |
| 2–11 | Network + Spring | Identical to Scenario 1 |
| 12 | HotelRepository | Hotel + room type found ✅ |
| 13 | BookingService | Capacity check passes ✅ |
| 14 | AvailabilityService | Acquire write lock |
| 15 | AvailabilityRepository | COUNT booked rooms |
| 16 | PostgreSQL | bookedRooms=10 — FULLY BOOKED |
| 17 | AvailabilityService | available=0 < requested=1 ❌ |
| 18 | AvailabilityService | Release write lock |
| 19 | BookingService | Throw RoomNotAvailableException |
| 20 | GlobalExceptionHandler | 409 Conflict |
| 21 | BookingController | Error with actionable suggestions |
| 22 | Return path | 409 in ~180ms (no BCrypt, no inserts) |
| 23 | Browser | "Room No Longer Available" with alternatives |