# Scenario 3: Booking With Promotion Code

**User:** Shawon (logged in)
**Action:** Books Deluxe King room with promo code "WELCOME10"
**Outcome:** Valid promotion applied, reduced total price, booking confirmed

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 3 |
|---|---|---|
| Promotion | None | WELCOME10 — 10% off for new users |
| Price calculation | RatePolicy only | RatePolicy + PromotionService |
| Total price | 20,250 THB | 18,225 THB (10% off) |
| Extra service call | None | PromotionService.validatePromotion() |
| Post-booking | applyPromotion() increments usageCount |

---

## The Cast

Same as Scenario 1, with additions:

| Character | Layer | Role |
|---|---|---|
| `PromotionService` | Domain | Validates and applies promotion |
| `PromotionRepository` | Data | Fetches and updates promotion |

---

## The Conversation

---

**Shawon:** I have a promo code WELCOME10. Let me use it when booking
the Grand Hyatt Deluxe King room.
*(enters promo code and clicks Book Now)*

---

**Browser:** Building HTTP POST with promotion code:

```
POST /api/v1/bookings
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
Content-Type: application/json

{
  "hotelId":       "hotel-001-uuid",
  "roomTypeId":    "rt-001-uuid",
  "checkIn":       "2024-12-20",
  "checkOut":      "2024-12-25",
  "guestCount":    2,
  "rooms":         1,
  "promotionCode": "WELCOME10"
}
```

Handing to TLS.

---

*(TLS → TCP/IP → Internet → CDN → API Gateway → Load Balancer → Nginx
→ Tomcat → DispatcherServlet → Spring Security Filter — identical to
Scenario 1. Picking up at BookingController.)*

---

**BookingController:** Deserializing request with promotion code:
```java
CreateBookingRequest request = new CreateBookingRequest(
    hotelId:       UUID("hotel-001-uuid"),
    roomTypeId:    UUID("rt-001-uuid"),
    userId:        UUID("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
    rooms:         1,
    guestCount:    2,
    checkIn:       LocalDate.of(2024, 12, 20),
    checkOut:      LocalDate.of(2024, 12, 25),
    promotionCode: "WELCOME10"    // new field
);
```

Calling BookingService.createBooking(request).

---

**BookingService:**

**Step 1 — Validate:** Valid ✅

**Step 2 — Look up hotel and room type:**
HotelRepository, fetch hotel and room type.

---

**HotelRepository:** (same SQL as Scenario 1) PostgreSQL returns hotel
and room type data. hotel ACTIVE, room type ACTIVE, capacity 2.

---

**BookingService:**

**Step 3 — Capacity check:** 2 ≤ 2 ✅

**Step 4 — Check availability inside write lock:**
AvailabilityService, acquire write lock and check rt-001-uuid Dec 20–25.

---

**AvailabilityService:** Write lock acquired.

AvailabilityRepository, count booked rooms.

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

PostgreSQL: booked_rooms = 3. Available = 7. ✅

---

**AvailabilityService:** 7 available ≥ 1 requested ✅
Returning true. Still holding write lock.

---

**BookingService:**

**Step 5 — Resolve RatePolicy:**
RatePolicy (rt-001-uuid), effective price for Dec 20–25?

---

**RatePolicy (rt-001-uuid):** Dec in range ✅. pricePerNight = 4500 THB.
DiscountPolicy apply.

---

**DiscountPolicy:** PERCENTAGE 10% — Year-End Special.
Effective: 4500 * 0.9 = 4050 THB/night.

---

**RatePolicy:** Returning 4050 THB/night.

---

**BookingService:**

**Step 5b — Validate and apply promotion:**
PromotionService, validate code "WELCOME10" for user
f47ac10b..., room type rt-001-uuid, base price 4050 THB/night
× 5 nights = 20,250 THB.

---

**PromotionService:** Received validation request.

**Step 1 — Look up promotion by code:**
PromotionRepository, find promotion with code "WELCOME10".

---

**PromotionRepository:**
```sql
SELECT id, code, description, scope, user_id, hotel_id,
       discount_type, discount_value,
       usage_limit, usage_limit_per_user, usage_count,
       valid_from, valid_to, status
FROM   promotions
WHERE  code   = 'WELCOME10'
AND    status = 'ACTIVE';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(promotions table)*
```
id:                 promo-001-uuid
code:               WELCOME10
description:        Welcome discount for new users
scope:              GLOBAL
user_id:            NULL     (global — not user-specific)
hotel_id:           NULL     (global — not hotel-specific)
discount_type:      PERCENTAGE
discount_value:     10.0
usage_limit:        1000
usage_limit_per_user: 1
usage_count:        0        (Shawon is the first user)
valid_from:         2024-01-01
valid_to:           2024-12-31
status:             ACTIVE
```

Row found.

---

**PromotionRepository:** Promotion found. Returning to PromotionService.

---

**PromotionService:**

**Step 2 — Check status:**
```
status = ACTIVE ✅
```

**Step 3 — Check expiry:**
```
validTo: 2024-12-31 > now: 2024-12-18 ✅ — not expired
validFrom: 2024-01-01 ≤ now ✅ — already active
```

**Step 4 — Check total usage limit:**
```
usageCount:  0
usageLimit:  1000
0 < 1000 ✅ — not exceeded
```

**Step 5 — Check per-user limit:**
PromotionRepository, how many times has user f47ac10b used
promo-001-uuid?

---

**PromotionRepository:**
```sql
SELECT COUNT(*) AS user_usage
FROM   bookings
WHERE  user_id      = 'f47ac10b-58cc-4372-a567-0e02b2c3d479'
AND    promotion_id = 'promo-001-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
user_usage
----------
0

Shawon has never used this promotion.
```

---

**PromotionRepository:** user_usage = 0. Returning to PromotionService.

---

**PromotionService:**

**Step 6 — Check per-user limit:**
```
userUsage:         0
usageLimitPerUser: 1
0 < 1 ✅ — Shawon can use this promotion
```

**Step 7 — Check scope:**
```
scope: GLOBAL — available to all users ✅
No userId restriction, no hotelId restriction
```

**Step 8 — Calculate discount:**
```
bookingPrice:   20,250 THB (base price before promotion)
discountType:   PERCENTAGE
discountValue:  10.0

discountAmount = 20,250 * (10/100) = 2,025 THB
finalPrice     = 20,250 - 2,025    = 18,225 THB
```

Building ValidatePromotionResult:
```java
ValidatePromotionResult result = new ValidatePromotionResult(
    promotionId:    UUID("promo-001-uuid"),
    code:           "WELCOME10",
    discountType:   DiscountType.PERCENTAGE,
    discountValue:  BigDecimal.valueOf(10.0),
    discountAmount: BigDecimal.valueOf(2025),
    finalPrice:     BigDecimal.valueOf(18225)
);
```

Returning to BookingService.

---

**BookingService:** Promotion validated. Final price is 18,225 THB.

**Step 6 — Create Booking with promotional price:**
```java
Booking booking = new Booking(
    id:               UUID.randomUUID(),
    bookingGroupId:   UUID.randomUUID(),
    hotelId:          UUID("hotel-001-uuid"),
    roomTypeId:       UUID("rt-001-uuid"),
    userId:           UUID("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
    promotionId:      UUID("promo-001-uuid"),   // linked to promotion
    rooms:            1,
    guestCount:       2,
    checkIn:          LocalDate.of(2024, 12, 20),
    checkOut:         LocalDate.of(2024, 12, 25),
    status:           BookingStatus.CONFIRMED,
    totalPrice:       BigDecimal.valueOf(18225),  // promotional price
    currencyCode:     "THB",
    cancelledAt:      null,
    cancellationReason: null,
    createdAt:        LocalDateTime.now(),
    updatedAt:        LocalDateTime.now()
);
```

BookingRepository, save this booking.

---

**BookingRepository:**
```sql
INSERT INTO bookings (
    id, booking_group_id, hotel_id, room_type_id, user_id,
    promotion_id, rooms, guest_count, check_in, check_out,
    status, total_price, currency_code,
    cancelled_at, cancellation_reason, created_at, updated_at
) VALUES (
    'g7h8i9j0-k1l2-3m4n-5o6p-7q8r9s0t1u2v',
    'h8i9j0k1-l2m3-4n5o-6p7q-8r9s0t1u2v3w',
    'hotel-001-uuid',
    'rt-001-uuid',
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'promo-001-uuid',
    1, 2,
    '2024-12-20', '2024-12-25',
    'CONFIRMED',
    18225.00,
    'THB',
    NULL, NULL,
    '2024-12-18T11:05:30Z',
    '2024-12-18T11:05:30Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
INSERT 0 1 ✅
```

---

**BookingRepository:** Booking saved. Returning to BookingService.

---

**BookingService:**

**Step 7 — Apply promotion (increment usage):**
AvailabilityService, release write lock first.

---

**AvailabilityService:** Releasing write lock.
```java
} finally {
    writeLock.unlock();
}
```

---

**BookingService:** Write lock released.

Now applying promotion — incrementing usage count:
PromotionService, apply promotion promo-001-uuid for user f47ac10b.

---

**PromotionService:** Incrementing usageCount.

PromotionRepository, increment usage for promo-001-uuid.

---

**PromotionRepository:**
```sql
UPDATE promotions
SET    usage_count = usage_count + 1,
       updated_at  = '2024-12-18T11:05:31Z'
WHERE  id = 'promo-001-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
UPDATE 1 ✅
usage_count: 0 → 1
```

---

**PromotionRepository:** Updated. Returning to PromotionService.

---

**PromotionService:** usageCount incremented. Returning to BookingService.

---

**BookingService:**

**Step 8 — Async tasks:**
DestinationService, increment Bangkok bookingCount. (async)
NotificationService, send confirmation email. (async)

*(Both proceed identically to Scenario 1 — DestinationService
increments count, NotificationService sends email with promotional
price highlighted.)*

The confirmation email now shows:
```
Room Rate:     20,250 THB  (4,050 THB/night × 5 nights)
WELCOME10:    -2,025 THB  (10% off)
Total:         18,225 THB  ✅
```

Returning confirmed Booking to BookingController.

---

**BookingController:** Serializing 201 response:

```json
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/bookings/g7h8i9j0-k1l2-3m4n-5o6p-7q8r9s0t1u2v

{
  "bookingId":      "g7h8i9j0-k1l2-3m4n-5o6p-7q8r9s0t1u2v",
  "hotelId":        "hotel-001-uuid",
  "hotelName":      "Grand Hyatt Bangkok",
  "roomTypeId":     "rt-001-uuid",
  "roomTypeName":   "Deluxe King",
  "checkIn":        "2024-12-20",
  "checkOut":       "2024-12-25",
  "guestCount":     2,
  "rooms":          1,
  "status":         "CONFIRMED",
  "basePrice":      20250.00,
  "discount":       2025.00,
  "promotionCode":  "WELCOME10",
  "totalPrice":     18225.00,
  "currencyCode":   "THB",
  "createdAt":      "2024-12-18T11:05:30Z"
}
```

---

*(Return path identical to Scenario 1.)*

---

**Browser:** Rendering:

```
✅ Booking Confirmed!

Grand Hyatt Bangkok — Deluxe King Room

Check-in:  December 20, 2024
Check-out: December 25, 2024

Room Rate:    20,250 THB
WELCOME10:   -2,025 THB   (10% off)
Total:        18,225 THB  🎉

Booking ID: g7h8i9j0-...

[View Booking]   [Search More Hotels]
```

---

**Shawon:** Excellent! The promo code saved me 2,025 THB. Bangkok
here I come!

---

## What Happens if Promotion is Invalid?

**Expired promotion:**
```java
// validTo: 2023-12-31 < now: 2024-12-18
throw new PromotionExpiredException("Promotion WELCOME10 has expired.");
// → 400 Bad Request
```

**Already used (usageLimitPerUser exceeded):**
```java
// userUsage: 1 >= usageLimitPerUser: 1
throw new PromotionUserLimitException(
    "You have already used promotion WELCOME10."
);
// → 409 Conflict
```

**Total limit reached:**
```java
// usageCount: 1000 >= usageLimit: 1000
throw new PromotionUsageLimitException(
    "Promotion WELCOME10 is no longer available."
);
// → 409 Conflict
```

**Invalid code:**
```java
// No promotion found with code "INVALID"
throw new ResourceNotFoundException("Promotion code not found.");
// → 404 Not Found
```

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Books with promo code WELCOME10 |
| 2 | Browser | HTTP POST with promotionCode field |
| 3–11 | Network + Spring | Identical to Scenario 1 |
| 12 | HotelRepository | Fetch hotel + room type ✅ |
| 13 | AvailabilityService | Write lock — 7 available ✅ |
| 14 | RatePolicy + DiscountPolicy | 4050 THB/night (10% Year-End) |
| 15 | PromotionService | Validate WELCOME10 |
| 16 | PromotionRepository | SELECT promotion by code |
| 17 | PostgreSQL | Promotion found — ACTIVE, not expired |
| 18 | PromotionService | Status ✅, expiry ✅, total usage ✅ |
| 19 | PromotionRepository | User usage count = 0 ✅ |
| 20 | PromotionService | scope=GLOBAL ✅ |
| 21 | PromotionService | discount=2025 THB, final=18,225 THB |
| 22 | BookingRepository | INSERT booking — totalPrice=18,225 |
| 23 | PostgreSQL | Booking with promotional price committed |
| 24 | AvailabilityService | Release write lock |
| 25 | PromotionService | INCREMENT usageCount 0→1 |
| 26 | PostgreSQL | Promotion usage updated |
| 27 | DestinationService | Increment bookingCount async |
| 28 | NotificationService | Send confirmation with savings shown |
| 29 | BookingController | 201 Created — base, discount, total shown |
| 30 | Browser | "Saved 2,025 THB with WELCOME10" |