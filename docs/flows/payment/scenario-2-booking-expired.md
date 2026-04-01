# Payment Scenario 2: Booking Expired — Hold Timeout Before Payment

**User:** Shawon (authenticated)
**Booking:** Grand Hyatt Bangkok — Deluxe King, Dec 20–25, 2 guests, 1 room
**Situation:** Shawon created a booking but waited too long before paying.
The 15-minute hold has expired.
**Outcome:** Payment rejected, Shawon told to search again

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Builds and sends HTTP request |
| `API Gateway` | Infrastructure | Auth, routing, rate limiting |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `PaymentController` | Application | Maps HTTP to service call |
| `PaymentService` | Domain | Orchestrates payment logic |
| `BookingService` | Domain | Validates booking state |
| `BookingRepository` | Data | Reads booking record |
| `BookingExpiryScheduler` | Domain | Background job — expires stale bookings |
| `NotificationService` | Domain | Sends expiry warning async |
| `PostgreSQL` | Database | Stores all data |

---

## Background: What Already Happened

Shawon created a booking 20 minutes ago. At that point, BookingService set:

```
status:      PENDING_PAYMENT
expires_at:  2024-12-18T09:55:00Z   ← 15 minutes from creation
```

A background scheduler runs every minute to clean up stale bookings.

---

## The Conversation

---

**BookingExpiryScheduler** *(running at 09:56:00Z, 1 minute ago)*:

Scanning for expired bookings:

```sql
SELECT booking_id, user_id, room_type_id, check_in, check_out
FROM   bookings
WHERE  status     = 'PENDING_PAYMENT'
  AND  expires_at < NOW();
```

---

**PostgreSQL:**
```
booking_id:   booking-001-uuid
user_id:      shawon-uuid-here
room_type_id: rt-001-uuid
check_in:     2024-12-20
check_out:    2024-12-25
expires_at:   2024-12-18T09:55:00Z   ← expired 1 minute ago
```

Returning 1 expired booking.

---

**BookingExpiryScheduler:** Found 1 expired booking.

```sql
UPDATE bookings
SET    status     = 'EXPIRED',
       updated_at = NOW()
WHERE  booking_id = 'booking-001-uuid'
  AND  status     = 'PENDING_PAYMENT';  ← guard: only expire if still pending
```

---

**PostgreSQL:** Row updated. status = EXPIRED. Returning.

---

**BookingExpiryScheduler:** Booking expired. No availability block was
ever inserted (room was never confirmed), so no availability rollback
needed. Dispatching expiry notification async.

---

**NotificationService** *(async)*:

Sending push notification to Shawon's device:
```
Title: Your booking hold has expired
Body:  Your hold on Grand Hyatt Bangkok expired.
       Search again to rebook.
```

*(Shawon has not opened the app yet — notification sits in device tray.)*

---

**Shawon:** *(20 minutes after creating the booking)* — I have decided.
Let me pay now.
*(clicks "Pay Now")*

---

**Browser:** Building HTTP request:

```
POST /api/v1/payments
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
Content-Type: application/json

{
  "bookingId":       "booking-001-uuid",
  "paymentMethodId": "pm_1OaBC2LkdIwH",
  "amount":          20250.00,
  "currency":        "THB",
  "promoCode":       null
}
```

Sending through TLS → Internet → Cloudflare → API Gateway → Load
Balancer → Nginx → Tomcat → Spring Security → DispatcherServlet.
*(Infrastructure layers identical to happy path — omitted for brevity.)*

---

**PaymentController:** Deserialising body. Handing to PaymentService.

---

**PaymentService:** Received PaymentRequest.

**Step 1 — Validate booking:**

BookingService, confirm booking-001-uuid is payable.

---

**BookingService:** Looking up booking-001-uuid.

BookingRepository, fetch this booking.

---

**BookingRepository:**
```sql
SELECT booking_id, user_id, status, expires_at, total_amount
FROM   bookings
WHERE  booking_id = 'booking-001-uuid';
```

---

**PostgreSQL:**
```
booking_id:  booking-001-uuid
user_id:     shawon-uuid-here
status:      EXPIRED              ← set by scheduler 1 minute ago
expires_at:  2024-12-18T09:55:00Z
```

Returning to BookingRepository.

---

**BookingRepository:** Returning Booking entity to BookingService.

---

**BookingService:**
```
Ownership check:  booking.userId == shawon-uuid-here  ✅
Status check:     booking.status == PENDING_PAYMENT?   ✗ EXPIRED

→ Throwing BookingExpiredException("booking-001-uuid expired at 09:55:00Z")
```

---

**PaymentService:** Caught BookingExpiredException.

No charge attempted — Stripe was never called.
No PENDING payment record written.

Returning error to PaymentController.

---

**PaymentController:** Serialising error response:

```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-9b4c3d2e-5f6a-7b8c-9d0e

{
  "error":     "BOOKING_EXPIRED",
  "message":   "Your booking hold expired at 09:55 UTC. Please search again to rebook.",
  "bookingId": "booking-001-uuid",
  "expiredAt": "2024-12-18T09:55:00Z"
}
```

Response travels back through Nginx → Load Balancer → API Gateway →
Cloudflare → TLS → Browser.

---

**Browser:** Received 409. Rendering error state:

```
⚠️  Your booking has expired

Your hold on Grand Hyatt Bangkok
expired 1 minute ago.

Prices and availability may have changed.

[ Search Again ]
```

---

**Shawon:** Disappointed — but clicks "Search Again" to start over.

---

## Key Design Decisions

**Why 409 Conflict and not 400 Bad Request?**
The request is well-formed — the conflict is a state problem, not an
input problem. 409 signals "valid request, wrong state."

**Why does the scheduler guard with `AND status = 'PENDING_PAYMENT'`?**
Prevents a race condition where the scheduler and a concurrent payment
attempt both try to update the same row. Only one wins.

**Why is no availability rollback needed?**
The availability block (`availability_blocks` table) is only inserted
after a successful payment. An expired PENDING_PAYMENT booking never
held a slot in that table — the inventory was never consumed.

**Why not extend the hold on user activity?**
Extending holds silently would starve other users trying to book the
same room. The 15-minute limit is firm and communicated to Shawon at
booking creation time.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | BookingExpiryScheduler | Runs every minute — finds expired holds |
| 2 | PostgreSQL | Returns booking-001-uuid, status PENDING_PAYMENT, expired |
| 3 | BookingExpiryScheduler | UPDATE status = EXPIRED |
| 4 | PostgreSQL | Commit EXPIRED row |
| 5 | NotificationService | Push notification dispatched async |
| 6 | Shawon | Clicks Pay Now — 20 minutes after booking |
| 7 | Browser | POST /api/v1/payments |
| 8 | PaymentController | Deserialise request, hand to PaymentService |
| 9 | PaymentService | Ask BookingService to validate |
| 10 | BookingRepository | SELECT booking — returns status = EXPIRED |
| 11 | BookingService | Status check fails → BookingExpiredException |
| 12 | PaymentService | Caught exception — Stripe never called |
| 13 | PaymentController | 409 Conflict — BOOKING_EXPIRED |
| 14 | Browser | Render expiry message + Search Again button |
| 15 | Shawon | Starts a new search |