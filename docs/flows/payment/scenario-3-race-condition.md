# Payment Scenario 3: Room No Longer Available — Race Condition

**User:** Shawon (authenticated)
**Booking:** Grand Hyatt Bangkok — Deluxe King, Dec 20–25
**Situation:** Shawon and Nadia both searched, both created a
PENDING_PAYMENT booking for the last available Deluxe King room.
Nadia paid 30 seconds before Shawon. By the time Shawon pays,
the room is gone.
**Outcome:** Shawon's payment is blocked before Stripe is called.
Shawon is offered alternatives.

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | Our user — arriving second |
| `Nadia` | Client | The other user — paid first |
| `Browser` | Client | Builds and sends HTTP request |
| `PaymentController` | Application | Maps HTTP to service call |
| `PaymentService` | Domain | Orchestrates payment logic |
| `BookingService` | Domain | Validates booking state |
| `AvailabilityService` | Domain | Final availability re-check |
| `AvailabilityRepository` | Data | Queries real-time availability |
| `BookingRepository` | Data | Reads / writes booking records |
| `RecommendationService` | Domain | Suggests alternative rooms |
| `PostgreSQL` | Database | Source of truth for availability |

---

## Background: How Both Bookings Were Created

miniAgoda allows optimistic booking — a PENDING_PAYMENT booking does
**not** immediately consume an availability slot. Inventory is only
locked on confirmed payment. This maximises conversion (fewer "room
taken" errors during browsing) but requires a final re-check at payment
time.

```
Grand Hyatt Deluxe King — total inventory: 1 room

Nadia's booking:  booking-002-uuid  status: PENDING_PAYMENT  (created 09:40)
Shawon's booking: booking-001-uuid  status: PENDING_PAYMENT  (created 09:41)

availability_blocks for rt-001-uuid, Dec 20–25: (empty — no confirmed booking yet)
```

---

## The Conversation

---

## Nadia Pays First (09:47:00Z)

Nadia completes her payment. PaymentService runs its final
availability re-check:

```sql
SELECT COUNT(*) AS booked
FROM   availability_blocks
WHERE  room_type_id = 'rt-001-uuid'
  AND  check_in     < '2024-12-25'
  AND  check_out    > '2024-12-20';
```

**PostgreSQL:** booked = 0. Room available. ✅

Nadia's payment proceeds through Stripe. On success:

```sql
INSERT INTO availability_blocks
    (room_type_id, booking_id, check_in, check_out)
VALUES
    ('rt-001-uuid', 'booking-002-uuid', '2024-12-20', '2024-12-25');

UPDATE bookings SET status = 'CONFIRMED' WHERE booking_id = 'booking-002-uuid';
```

**PostgreSQL:** Both rows committed. The last Deluxe King room is now
occupied for Dec 20–25.

```
availability_blocks for rt-001-uuid, Dec 20–25:
  booking-002-uuid (Nadia) — CONFIRMED ✅
```

---

## Shawon Pays 30 Seconds Later (09:47:30Z)

---

**Shawon:** Clicks "Pay Now."

---

**Browser:** Building HTTP request:

```
POST /api/v1/payments
Authorization:   Bearer eyJhbGciOiJIUzI1NiJ9...
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
Content-Type:    application/json

{
  "bookingId":       "booking-001-uuid",
  "paymentMethodId": "pm_1OaBC2LkdIwH",
  "amount":          20250.00,
  "currency":        "THB"
}
```

Sending through infrastructure layers to PaymentController.
*(Infrastructure layers identical to happy path — omitted for brevity.)*

---

**PaymentController:** Deserialising body. Handing to PaymentService.

---

**PaymentService:** Received PaymentRequest.

**Step 1 — Validate booking:**

BookingService, confirm booking-001-uuid is payable.

---

**BookingService:**
```
Ownership:  shawon-uuid-here ✅
Status:     PENDING_PAYMENT ✅
Amount:     20250.00 ✅
Expiry:     09:56:00Z — still valid ✅
```

Booking is valid. Returning to PaymentService.

---

**PaymentService:** Booking validated.

**Step 2 — Final availability re-check:**

AvailabilityService, is rt-001-uuid still available for Dec 20–25?

---

**AvailabilityService:**

AvailabilityRepository, query current confirmed blocks.

---

**AvailabilityRepository:**
```sql
SELECT COUNT(*) AS booked
FROM   availability_blocks
WHERE  room_type_id = 'rt-001-uuid'
  AND  check_in     < '2024-12-25'
  AND  check_out    > '2024-12-20';
```

---

**PostgreSQL:**
```
booked: 1   ← Nadia's confirmed booking, inserted 30 seconds ago
```

Returning to AvailabilityRepository.

---

**AvailabilityRepository:** Returning count to AvailabilityService.

---

**AvailabilityService:**
```
totalRooms:  1
booked:      1
available:   0
Required:    1
Status:      UNAVAILABLE ✗
```

Throwing RoomUnavailableException("rt-001-uuid has no availability for
Dec 20–25").

---

**PaymentService:** Caught RoomUnavailableException.

Stripe was never called. No PENDING payment record written.

**Step 3 — Cancel Shawon's stale booking:**

```sql
UPDATE bookings
SET    status     = 'CANCELLED',
       cancel_reason = 'ROOM_TAKEN',
       updated_at = NOW()
WHERE  booking_id = 'booking-001-uuid'
  AND  status     = 'PENDING_PAYMENT';
```

---

**PostgreSQL:** Row updated. Booking cancelled. Returning.

---

**PaymentService:** Booking cancelled.

**Step 4 — Ask RecommendationService for alternatives:**

RecommendationService, find available Deluxe or Superior rooms in
Bangkok for Dec 20–25, 2 guests — similar price range to 4050 THB/night.

---

**RecommendationService:**

Querying hotels with availability for the same dates and similar spec:

```sql
SELECT h.hotel_id, h.name, h.rating,
       rt.room_type_id, rt.name AS room_name,
       rp.price_per_night
FROM   hotels h
JOIN   room_types rt ON rt.hotel_id = h.hotel_id
JOIN   rate_policies rp ON rp.room_type_id = rt.room_type_id
WHERE  h.city_id     = '550e8400-e29b-41d4-a716-446655440000'
  AND  rt.category   IN ('DELUXE', 'SUPERIOR')
  AND  rp.price_per_night BETWEEN 3000 AND 5500
  AND  rt.room_type_id NOT IN (
         SELECT room_type_id FROM availability_blocks
         WHERE  check_in  < '2024-12-25'
           AND  check_out > '2024-12-20'
       )
ORDER BY h.rating DESC
LIMIT 3;
```

---

**PostgreSQL:**
```
1. Chatrium Hotel Bangkok — Superior King — 3200 THB/night — rating 8.7
2. Sindhorn Midtown Bangkok — Deluxe Queen — 3800 THB/night — rating 8.5
3. The Athenee Hotel — Superior Twin — 3500 THB/night — rating 8.4
```

Returning to RecommendationService.

---

**RecommendationService:** Returning 3 alternatives to PaymentService.

---

**PaymentService:** Returning RoomUnavailableException + alternatives
to PaymentController.

---

**PaymentController:** Serialising error response:

```json
HTTP/1.1 409 Conflict
Content-Type: application/json
X-Request-ID: req-9b4c3d2e-5f6a-7b8c-9d0e

{
  "error":   "ROOM_NO_LONGER_AVAILABLE",
  "message": "Sorry — the last Deluxe King room at Grand Hyatt Bangkok was just booked.",
  "alternatives": [
    {
      "hotelId":      "hotel-003-uuid",
      "name":         "Chatrium Hotel Bangkok",
      "roomType":     "Superior King",
      "pricePerNight": 3200.00,
      "rating":       8.7
    },
    {
      "hotelId":      "hotel-005-uuid",
      "name":         "Sindhorn Midtown Bangkok",
      "roomType":     "Deluxe Queen",
      "pricePerNight": 3800.00,
      "rating":       8.5
    },
    {
      "hotelId":      "hotel-007-uuid",
      "name":         "The Athenee Hotel",
      "roomType":     "Superior Twin",
      "pricePerNight": 3500.00,
      "rating":       8.4
    }
  ]
}
```

Response travels back to Browser.

---

**Browser:** Received 409. Rendering:

```
😔  This room was just taken

The last Deluxe King at Grand Hyatt Bangkok
was booked moments ago.

You might like these instead:

⭐ 8.7  Chatrium Hotel Bangkok
        Superior King — 3,200 THB/night
        [ Book Now ]

⭐ 8.5  Sindhorn Midtown Bangkok
        Deluxe Queen — 3,800 THB/night
        [ Book Now ]

⭐ 8.4  The Athenee Hotel
        Superior Twin — 3,500 THB/night
        [ Book Now ]
```

---

**Shawon:** Disappointed — but Chatrium looks good. Clicks "Book Now"
on Chatrium.

---

## Key Design Decisions

**Why use optimistic booking instead of reserving inventory immediately?**
Reserving on browse would cause most rooms to appear taken (users browse
many options before committing). Optimistic booking maximises visible
availability, and the final re-check at payment time catches the rare
true conflict.

**Why is the re-check inside a transaction with the INSERT?**
The re-check query and the `INSERT INTO availability_blocks` must happen
in the same database transaction. If they were separate, two threads
could both pass the re-check and both insert — double-booking the room.

```sql
BEGIN;
  SELECT COUNT(*) FROM availability_blocks WHERE ... FOR UPDATE;
  -- if 0, proceed:
  INSERT INTO availability_blocks ...;
COMMIT;
```

The `FOR UPDATE` on the SELECT locks the rows being checked, preventing
a concurrent transaction from inserting between the check and the insert.

**Why cancel Shawon's booking instead of leaving it PENDING_PAYMENT?**
A cancelled booking makes the state explicit. Leaving it PENDING would
mislead Shawon into thinking she still has a hold. It also keeps the
bookings table clean for the expiry scheduler.

**Why offer alternatives in the same response?**
Minimises round trips. Shawon is already on the payment page and
frustrated — surfacing alternatives immediately (rather than redirecting
to search) reduces abandonment.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Nadia | Pays 30 seconds earlier — room confirmed |
| 2 | AvailabilityRepository | INSERT availability_blocks for Nadia |
| 3 | PostgreSQL | Room now fully booked for Dec 20–25 |
| 4 | Shawon | Clicks Pay Now |
| 5 | Browser | POST /api/v1/payments |
| 6 | PaymentService | Validate booking — PENDING_PAYMENT ✅ |
| 7 | AvailabilityService | Final re-check |
| 8 | AvailabilityRepository | SELECT count — returns 1 (Nadia's block) |
| 9 | AvailabilityService | available = 0 → RoomUnavailableException |
| 10 | PaymentService | Stripe never called |
| 11 | BookingRepository | UPDATE booking = CANCELLED (ROOM_TAKEN) |
| 12 | PostgreSQL | Booking cancelled |
| 13 | RecommendationService | Query 3 alternative rooms |
| 14 | PostgreSQL | Return available alternatives |
| 15 | PaymentController | 409 Conflict with alternatives |
| 16 | Browser | Render "room taken" + 3 alternatives |
| 17 | Shawon | Selects Chatrium — starts new booking |