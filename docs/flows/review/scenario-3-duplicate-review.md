# Scenario 3: Review — Duplicate Review

**User:** Shawon (logged in, already reviewed this booking in Scenario 1)
**Action:** Tries to submit a second review for the same booking
**Outcome:** 409 Conflict — one review per booking enforced

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 3 |
|---|---|---|
| Existing review | None | Already submitted |
| Duplicate check | Count=0 ✅ | Count=1 ❌ |
| Review created | Yes | No |
| Rating recalculated | Yes | No |
| HTTP Status | 201 Created | 409 Conflict |
| Response time | ~287ms | ~65ms |

---

## The Conversation

*(Network layers identical. Picking up at ReviewService.)*

---

**ReviewService:** Received CreateReviewRequest for booking d4e5f6g7...

**Step 1 — Look up booking:**
BookingRepository, find booking d4e5f6g7...

---

**BookingRepository:**
```sql
SELECT id, user_id, hotel_id, status
FROM   bookings
WHERE  id = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s';
```

PostgreSQL: status=COMPLETED ✅

---

**ReviewService:**

**Step 2 — Ownership verified:** ✅

**Step 3 — Booking status:** COMPLETED ✅

**Step 4 — Check for duplicate review:**
ReviewRepository, does a review already exist for booking d4e5f6g7...?

---

**ReviewRepository:**
```sql
SELECT COUNT(*)
FROM   reviews
WHERE  booking_id = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(reviews table)*
```
count
-----
1

Shawon's review from Scenario 1 already exists:
id: j0k1l2m3-n4o5-6p7q-8r9s-0t1u2v3w4x5y
```

---

**ReviewRepository:** Count = 1. Review already exists. Returning to
ReviewService.

---

**ReviewService:** Duplicate detected.

Throwing DuplicateReviewException:
```java
throw new DuplicateReviewException(
    "You have already reviewed this booking. " +
    "Each booking can only have one review. " +
    "You can edit your existing review instead."
);
```

---

**GlobalExceptionHandler:**
```java
@ExceptionHandler(DuplicateReviewException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleDuplicateReview(DuplicateReviewException ex) {
    return new ErrorResponse(
        status:  409,
        error:   "Conflict",
        message: ex.getMessage(),
        path:    "/api/v1/reviews",
        action:  "edit-existing-review",
        existingReviewId: "j0k1l2m3-n4o5-6p7q-8r9s-0t1u2v3w4x5y"
    );
}
```

---

**ReviewController:** Serializing 409:

```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-2l3m4n5o-6p7q-8r9s-0t1u

{
  "status":           409,
  "error":            "Conflict",
  "message":          "You have already reviewed this booking. Each booking can only have one review. You can edit your existing review instead.",
  "path":             "/api/v1/reviews",
  "action":           "edit-existing-review",
  "existingReviewId": "j0k1l2m3-n4o5-6p7q-8r9s-0t1u2v3w4x5y"
}
```

Note: `existingReviewId` is included so the frontend can redirect
Shawon directly to edit his existing review rather than showing
a dead end.

---

**Browser:** Received 409. Rendering:

```
⚠️ Already Reviewed

You have already reviewed this booking.
Each booking can only have one review.

Would you like to edit your existing review?

[Edit My Review]   [View My Review]   [Back]
```

---

**Shawon:** Oh right — I already left a review. Let me edit it instead
if I want to make changes.

---

## Design Insight — One Review Per Booking, Not Per Hotel

Shawon can review the same hotel multiple times — once per completed
booking. This is an intentional design decision:

```
Booking A (Nov 10–15): Review allowed ✅
Booking B (Dec 20–25): Review allowed ✅
Both are for Grand Hyatt — both valid separate experiences

But:
Booking B second review attempt: ❌ — duplicate for same booking
```

The uniqueness constraint is on `booking_id`, not on `(user_id, hotel_id)`.
This ensures each review reflects a specific, verifiable stay.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Tries to submit second review for same booking |
| 2–10 | Network + Spring | Identical to Scenario 1 |
| 11 | ReviewService | Look up booking — COMPLETED ✅ |
| 12 | ReviewService | Ownership ✅, status ✅ |
| 13 | ReviewRepository | SELECT COUNT(*) WHERE booking_id=? |
| 14 | PostgreSQL | Count=1 — duplicate detected ❌ |
| 15 | ReviewService | Throw DuplicateReviewException |
| 16 | GlobalExceptionHandler | 409 Conflict |
| 17 | ReviewController | existingReviewId in response |
| 18 | Return path | 409 in ~65ms |
| 19 | Browser | "Edit existing review" redirect |