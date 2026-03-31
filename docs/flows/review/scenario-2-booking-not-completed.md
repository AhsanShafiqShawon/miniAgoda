# Scenario 2: Review — Booking Not Completed

**User:** Shawon (logged in)
**Action:** Tries to review a booking that is still CONFIRMED (future stay)
**Outcome:** 409 Conflict — cannot review before checking out

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 2 |
|---|---|---|
| Booking status | COMPLETED | CONFIRMED |
| Status check | Passes | Fails immediately |
| Review created | Yes | No |
| Rating recalculated | Yes | No |
| HTTP Status | 201 Created | 409 Conflict |
| Response time | ~287ms | ~60ms |

---

## The Conversation

*(Network layers identical. Picking up at ReviewService.)*

---

**ReviewService:** Received CreateReviewRequest.

**Step 1 — Look up booking:**
BookingRepository, find booking d4e5f6g7...

---

**BookingRepository:**
```sql
SELECT id, user_id, hotel_id, status, check_out
FROM   bookings
WHERE  id = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
id:        d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s
user_id:   f47ac10b-58cc-4372-a567-0e02b2c3d479
hotel_id:  hotel-001-uuid
status:    CONFIRMED    ← stay has not happened yet
check_out: 2024-12-25   (future date)
```

---

**BookingRepository:** Booking found. Returning to ReviewService.

---

**ReviewService:**

**Step 2 — Verify ownership:** ✅

**Step 3 — Verify booking status:**
```
booking.status: CONFIRMED ❌
```

Cannot review a booking that has not been completed.

Throwing InvalidReviewRequestException:
```java
throw new InvalidReviewRequestException(
    "You can only review a booking after your stay is complete. " +
    "Your check-out date is December 25, 2024."
);
```

No review created. No rating recalculated. No email sent.

---

**GlobalExceptionHandler:**
```java
@ExceptionHandler(InvalidReviewRequestException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleInvalidReviewRequest(
    InvalidReviewRequestException ex) {
    return new ErrorResponse(
        status:  409,
        error:   "Conflict",
        message: ex.getMessage(),
        path:    "/api/v1/reviews"
    );
}
```

---

**ReviewController:** Serializing 409:

```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-1k2l3m4n-5o6p-7q8r-9s0t

{
  "status":  409,
  "error":   "Conflict",
  "message": "You can only review a booking after your stay is complete. Your check-out date is December 25, 2024.",
  "path":    "/api/v1/reviews"
}
```

---

**Browser:** Received 409. Rendering:

```
⚠️ Stay Not Yet Completed

You can only write a review after your stay is complete.
Your check-out date is December 25, 2024.

Come back after your stay to share your experience!

[View My Booking]   [Back]
```

---

**Shawon:** Right — I haven't checked out yet. I'll come back after
my stay.

---

## Also Applies to CANCELLED Bookings

The same rejection applies if Shawon tries to review a CANCELLED booking:

```
booking.status: CANCELLED ❌

throw new InvalidReviewRequestException(
    "You cannot review a cancelled booking."
);

→ 409 Conflict
  "You cannot review a cancelled booking."
```

A cancelled booking means the stay never happened — no review possible.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Tries to review future/active booking |
| 2–10 | Network + Spring | Identical to Scenario 1 |
| 11 | ReviewService | Look up booking |
| 12 | BookingRepository | SELECT booking — status=CONFIRMED |
| 13 | ReviewService | Ownership verified ✅ |
| 14 | ReviewService | status=CONFIRMED ❌ — throw immediately |
| 15 | GlobalExceptionHandler | 409 Conflict with check-out date |
| 16 | Return path | 409 in ~60ms |
| 17 | Browser | "Come back after your stay" message |