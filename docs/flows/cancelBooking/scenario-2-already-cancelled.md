# Scenario 2: Cancel Booking — Already Cancelled

**User:** Shawon (logged in)
**Action:** Tries to cancel a booking that is already CANCELLED
**Outcome:** 409 Conflict — double cancellation rejected

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 2 |
|---|---|---|
| Booking status | CONFIRMED | CANCELLED |
| Status check | Passes | Fails immediately |
| Inventory released | Yes | No — already released |
| Refund initiated | Yes | No — already refunded |
| HTTP Status | 200 OK | 409 Conflict |
| Response time | ~534ms | ~80ms (no Stripe, no async tasks) |

---

## The Conversation

*(Network layers — identical to Scenario 1. Picking up at BookingService.)*

---

**BookingService:** Received cancellation request for booking
d4e5f6g7...

**Step 1 — Look up booking:**
BookingRepository, find d4e5f6g7...

---

**BookingRepository:**
```sql
SELECT id, user_id, status, cancelled_at,
       cancellation_reason, ...
FROM   bookings
WHERE  id = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
id:              d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s
user_id:         f47ac10b-58cc-4372-a567-0e02b2c3d479
status:          CANCELLED    ← already cancelled in Scenario 1
cancelled_at:    2024-12-18T14:30:00Z
cancellation_reason: Cancelled by guest
```

---

**BookingRepository:** Booking found. Returning to BookingService.

---

**BookingService:**

**Step 2 — Verify ownership:** ✅

**Step 3 — Check booking status:**
```
booking.status: CANCELLED ❌
```

Cannot cancel an already cancelled booking.

Throwing InvalidBookingStateException:
```java
throw new InvalidBookingStateException(
    "Booking d4e5f6g7-... is already cancelled. " +
    "Cancelled on: 2024-12-18T14:30:00Z"
);
```

No inventory changes. No Stripe calls. No emails. Nothing.

---

**GlobalExceptionHandler:**
```java
@ExceptionHandler(InvalidBookingStateException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleInvalidBookingState(InvalidBookingStateException ex) {
    return new ErrorResponse(
        status:  409,
        error:   "Conflict",
        message: ex.getMessage(),
        path:    "/api/v1/bookings/d4e5f6g7-.../cancel"
    );
}
```

---

**BookingController:** Serializing 409 response:

```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-8h9i0j1k-2l3m-4n5o-6p7q

{
  "status":  409,
  "error":   "Conflict",
  "message": "Booking d4e5f6g7-... is already cancelled. Cancelled on: 2024-12-18T14:30:00Z",
  "path":    "/api/v1/bookings/d4e5f6g7-.../cancel"
}
```

---

*(Return path — identical structure to Scenario 1 return path.)*

---

**Browser:** Received 409. Rendering:

```
⚠️ Already Cancelled

This booking was already cancelled on
December 18, 2024 at 2:30 PM.

If you have questions about your refund,
please contact support.

[View My Bookings]   [Contact Support]
```

---

**Shawon:** Ah, I already cancelled this booking earlier.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Tries to cancel already-cancelled booking |
| 2–11 | Network + Spring | Identical to Scenario 1 |
| 12 | BookingRepository | SELECT booking — status=CANCELLED |
| 13 | BookingService | Ownership verified ✅ |
| 14 | BookingService | status=CANCELLED ❌ — throw immediately |
| 15 | GlobalExceptionHandler | 409 Conflict |
| 16 | Return path | 409 in ~80ms — no Stripe, no async tasks |
| 17 | Browser | "Already cancelled" message |