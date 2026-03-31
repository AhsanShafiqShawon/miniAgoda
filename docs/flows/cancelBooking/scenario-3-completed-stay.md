# Scenario 3: Cancel Booking — Completed Stay

**User:** Shawon (logged in)
**Action:** Tries to cancel a booking with status COMPLETED
**Outcome:** 409 Conflict — cannot cancel a stay that has already happened

---

## Key Differences from Scenarios 1 & 2

| Aspect | Scenario 1 | Scenario 2 | Scenario 3 |
|---|---|---|---|
| Booking status | CONFIRMED | CANCELLED | COMPLETED |
| Reason | — | Already cancelled | Stay already finished |
| HTTP Status | 200 OK | 409 Conflict | 409 Conflict |
| Message | — | "Already cancelled" | "Stay already completed" |
| Action hint | — | contact-support | write-review |

---

## The Conversation

*(Network layers — identical to Scenario 1. Picking up at BookingService.)*

---

**BookingService:** Received cancellation request.

**Step 1 — Look up booking:**
BookingRepository, find the booking.

---

**BookingRepository:**
```sql
SELECT id, user_id, status, check_in, check_out, ...
FROM   bookings
WHERE  id = 'booking-completed-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
id:         booking-completed-uuid
user_id:    f47ac10b-58cc-4372-a567-0e02b2c3d479
status:     COMPLETED    ← stay has finished
check_in:   2024-11-10
check_out:  2024-11-15
```

---

**BookingRepository:** Booking found. Returning to BookingService.

---

**BookingService:**

**Step 2 — Verify ownership:** ✅

**Step 3 — Check booking status:**
```
booking.status: COMPLETED ❌
```

Cannot cancel a completed stay. The dates have passed, the guest
has checked out.

Throwing InvalidBookingStateException:
```java
throw new InvalidBookingStateException(
    "Booking booking-completed-uuid cannot be cancelled. " +
    "The stay was completed on November 15, 2024. " +
    "You can still write a review for this stay."
);
```

Note: The message includes a helpful hint — "You can still write
a review." This turns a rejection into an opportunity for
engagement.

---

**GlobalExceptionHandler:** 409 Conflict.

---

**BookingController:** Serializing 409:

```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-9i0j1k2l-3m4n-5o6p-7q8r

{
  "status":  409,
  "error":   "Conflict",
  "message": "Booking cannot be cancelled. The stay was completed on November 15, 2024.",
  "path":    "/api/v1/bookings/booking-completed-uuid/cancel",
  "action":  "write-review"
}
```

---

*(Return path — identical to Scenarios 1 and 2.)*

---

**Browser:** Received 409. Rendering:

```
⚠️ Cannot Cancel Completed Stay

This booking was completed on November 15, 2024.
Completed stays cannot be cancelled.

How was your stay at Grand Hyatt Bangkok?

[Write a Review]   [View My Bookings]
```

---

**Shawon:** Right — that stay already happened. I should write a review
instead.

---

## How COMPLETED Status is Set

For context — COMPLETED is set automatically by a scheduled job
that runs nightly:

```java
@Scheduled(cron = "0 0 2 * * *")  // 2 AM every night
public void completeFinishedBookings() {
    bookingRepository.completeFinishedBookings(LocalDate.now());
}
```

```sql
UPDATE bookings
SET    status     = 'COMPLETED',
       updated_at = NOW()
WHERE  status    = 'CONFIRMED'
AND    check_out < CURRENT_DATE;
```

This runs nightly and transitions all CONFIRMED bookings where
`checkOut` is in the past to COMPLETED.

---

## Booking Status Transition Rules

```
CONFIRMED → CANCELLED   (cancelBooking — before checkOut date)
CONFIRMED → COMPLETED   (scheduled job — after checkOut date)
CANCELLED → (terminal)  (no further transitions)
COMPLETED → (terminal)  (no further transitions)
```

Neither CANCELLED nor COMPLETED can transition to any other state.
They are terminal states.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Tries to cancel completed booking |
| 2–11 | Network + Spring | Identical to Scenario 1 |
| 12 | BookingRepository | SELECT booking — status=COMPLETED |
| 13 | BookingService | Ownership verified ✅ |
| 14 | BookingService | status=COMPLETED ❌ — throw immediately |
| 15 | GlobalExceptionHandler | 409 Conflict |
| 16 | BookingController | "write-review" action hint |
| 17 | Return path | 409 in ~80ms |
| 18 | Browser | "Cannot cancel" + "Write a Review" button |