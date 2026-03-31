# Scenario 1: Successful Booking Cancellation

**User:** Shawon (logged in, has a CONFIRMED booking)
**Action:** Cancels his Grand Hyatt Bangkok booking
**Outcome:** Booking CANCELLED, inventory released, refund initiated, notification sent

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Builds HTTP DELETE/PATCH |
| `TLS (Client)` | Client OS | Encrypts request |
| `TCP/IP (Client)` | Client OS | Routes packets |
| `Internet` | Network | Routes to server |
| `CDN (Cloudflare)` | Edge | TLS termination |
| `API Gateway` | Infrastructure | Auth, routing, rate limiting |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `Reverse Proxy (Nginx)` | Infrastructure | HTTP parsing |
| `TCP/IP (Server)` | Server OS | Reassembles packets |
| `HTTP Server (Tomcat)` | Server | Parses HTTP |
| `DispatcherServlet` | Spring | Routes to handler |
| `Spring Security Filter` | Spring | JWT validation |
| `BookingController` | Application | Maps request to service |
| `BookingService` | Domain | Orchestrates cancellation |
| `BookingRepository` | Data | Fetches and updates booking |
| `AvailabilityService` | Domain | Releases blocked rooms |
| `AvailabilityRepository` | Data | Updates availability |
| `PaymentService` | Domain | Initiates refund |
| `PaymentRepository` | Data | Fetches payment record |
| `RefundRepository` | Data | Creates refund record |
| `PaymentGateway` | Infrastructure | Stripe refund API |
| `DestinationService` | Domain | Decrements booking count async |
| `NotificationService` | Domain | Sends cancellation email async |
| `NotificationRepository` | Data | Persists notification |
| `EmailGateway` | Infrastructure | Sends email via SMTP |
| `PostgreSQL` | Database | Executes all queries |

---

## The Conversation

---

**Shawon:** I need to cancel my Grand Hyatt Bangkok booking.
My plans changed.
*(navigates to My Bookings → clicks Cancel)*

---

**Browser:** Building HTTP PATCH request. Using PATCH because we are
partially updating the booking resource — changing its status to
CANCELLED without replacing the entire resource.

```
PATCH /api/v1/bookings/d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s/cancel
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
Accept: application/json
Content-Length: 0
```

No request body — the booking ID in the URL is sufficient. Handing to TLS.

---

**TLS Layer (Client):** Existing TLS 1.3 session. Encrypting with
AES-256-GCM. Handing to TCP/IP.

---

**TCP/IP (Client OS):**
```
Packet 1:
  Source IP:        192.168.1.105
  Destination IP:   104.21.45.23
  Source Port:      58901
  Destination Port: 443
  Sequence No:      1001
```

Routing to Cloudflare Bangkok.

---

**Internet:** ~3ms to Cloudflare Bangkok PoP.

---

**CDN (Cloudflare):**

**TLS Termination:**
```
PATCH /api/v1/bookings/d4e5f6g7-.../cancel
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Cache check:** PATCH — never cached. State-modifying request.

**Bot protection:** Normal request rate. PASS ✅

Forwarding to API Gateway.

---

**API Gateway:**

**Rate limiting:**
```
Key:    rate_limit:user:f47ac10b...:cancel
Count:  1 attempt
Limit:  10 cancellations per hour
Status: PASS ✅
```

**JWT expiry:** valid ✅

**Routing:**
```
PATCH /api/v1/bookings/d4e5f6g7-.../cancel
→ miniagoda-app:8080
```

Forwarding with X-Request-ID: req-7g8h9i0j-1k2l-3m4n-5o6p

---

**Load Balancer:**
```
Selected: miniagoda-app-1:8080 (least connections)
```

---

**Reverse Proxy (Nginx):** Forwarding to Tomcat on app-1.

---

**TCP/IP (Server OS):** Reassembling. Placing in Tomcat buffer.

---

**HTTP Server (Tomcat):** Thread picked up request.
```
Method: PATCH
URI:    /api/v1/bookings/d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s/cancel
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:** JWT present.

**Decode:**
```
Payload: {
  "sub":  "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "role": "GUEST",
  "exp":  1703778923
}
Signature: verified ✅
Expiry:    valid ✅
```

**Populate SecurityContext:**
```java
new UsernamePasswordAuthenticationToken(
    "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    null, [ROLE_GUEST]
)
```

No database call. Passing through.

---

**DispatcherServlet:** Routing:
```
PATCH /api/v1/bookings/{bookingId}/cancel
→ BookingController.cancelBooking()
  @PatchMapping("/api/v1/bookings/{bookingId}/cancel")
  @PreAuthorize("hasRole('GUEST')")
```

---

**BookingController:** Received request.

Extracting from URL and SecurityContext:
```java
UUID bookingId = UUID("d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s");
UUID userId    = UUID("f47ac10b-58cc-4372-a567-0e02b2c3d479");
```

Calling BookingService.cancelBooking(bookingId, userId).

---

**BookingService:** Received cancellation request.

**Step 1 — Look up booking:**
BookingRepository, find booking d4e5f6g7...

---

**BookingRepository:** Generating SQL:
```sql
SELECT id, booking_group_id, hotel_id, room_type_id, user_id,
       rooms, guest_count, check_in, check_out,
       status, total_price, currency_code,
       cancelled_at, cancellation_reason,
       created_at, updated_at
FROM   bookings
WHERE  id = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(bookings table)*
```
id:               d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s
booking_group_id: e5f6g7h8-i9j0-1k2l-3m4n-5o6p7q8r9s0t
hotel_id:         hotel-001-uuid
room_type_id:     rt-001-uuid
user_id:          f47ac10b-58cc-4372-a567-0e02b2c3d479
rooms:            1
guest_count:      2
check_in:         2024-12-20
check_out:        2024-12-25
status:           CONFIRMED
total_price:      20250.00
currency_code:    THB
cancelled_at:     NULL
cancellation_reason: NULL
created_at:       2024-12-18T11:00:15Z
updated_at:       2024-12-18T11:00:15Z
```

Row found.

---

**BookingRepository:** Booking found. Returning to BookingService.

---

**BookingService:**

**Step 2 — Verify ownership:**
```
booking.userId: f47ac10b-58cc-4372-a567-0e02b2c3d479
requestUserId:  f47ac10b-58cc-4372-a567-0e02b2c3d479
Match ✅ — Shawon owns this booking
```

If IDs did not match, throw UnauthorizedException — users can only
cancel their own bookings.

**Step 3 — Check booking status:**
```
booking.status: CONFIRMED
CONFIRMED → cancellable ✅
```

Not CANCELLED (already done) — would throw InvalidBookingStateException.
Not COMPLETED (stay finished) — would throw InvalidBookingStateException.

**Step 4 — Update booking status:**
BookingRepository, mark this booking as CANCELLED.

---

**BookingRepository:** Generating SQL:
```sql
UPDATE bookings
SET    status              = 'CANCELLED',
       cancelled_at        = '2024-12-18T14:30:00Z',
       cancellation_reason = 'Cancelled by guest',
       updated_at          = '2024-12-18T14:30:00Z'
WHERE  id     = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s'
AND    status = 'CONFIRMED';
```

Note: `AND status = 'CONFIRMED'` — optimistic concurrency check.
If another request already cancelled this booking, UPDATE affects
0 rows and we know there was a race condition.

PostgreSQL, execute.

---

**PostgreSQL:** *(bookings table)*
```
Before: status=CONFIRMED, cancelled_at=NULL
After:  status=CANCELLED, cancelled_at=2024-12-18T14:30:00Z

UPDATE 1 ✅
```

---

**BookingRepository:** Updated. Returning to BookingService.

---

**BookingService:**

**Step 5 — Release inventory:**
AvailabilityService, release 1 room of rt-001-uuid for Dec 20–25.

---

**AvailabilityService:** Acquiring write lock.

```java
writeLock.lock();
```

Write lock acquired. No other booking or cancellation can modify
availability until I release.

AvailabilityRepository, verify current availability and confirm
release is consistent.

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

**PostgreSQL:** *(bookings table — booking now CANCELLED)*
```
booked_rooms
------------
2

Was 3 before cancellation. Now 2 because Shawon's booking
is CANCELLED and excluded from the SUM.
```

---

**AvailabilityRepository:** booked_rooms = 2. Returning to
AvailabilityService.

---

**AvailabilityService:**
```
totalRooms:  10
bookedRooms: 2  (was 3, now 2 after cancellation)
available:   8  (was 7, now 8 — room released back)
```

Room successfully released. The availability is naturally reflected
by the booking status change — no separate counter to update.

Releasing write lock:
```java
} finally {
    writeLock.unlock();
}
```

Returning to BookingService.

---

**BookingService:**

**Step 6 — Initiate refund:**
PaymentService, process refund for booking
d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s.

---

**PaymentService:** Received refund request.

**Step 1 — Look up payment for this booking:**
PaymentRepository, find the completed payment for booking
d4e5f6g7...

---

**PaymentRepository:**
```sql
SELECT id, booking_id, user_id,
       amount, currency_code,
       payment_method, status,
       gateway_transaction_id, gateway_provider,
       created_at, updated_at
FROM   payments
WHERE  booking_id = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s'
AND    status     = 'COMPLETED';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(payments table)*
```
id:                      pay-001-uuid
booking_id:              d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s
user_id:                 f47ac10b-58cc-4372-a567-0e02b2c3d479
amount:                  20250.00
currency_code:           THB
payment_method:          CREDIT_CARD
status:                  COMPLETED
gateway_transaction_id:  pi_3N8abcDEFGhij123  (Stripe payment intent ID)
gateway_provider:        STRIPE
created_at:              2024-12-18T11:00:20Z
updated_at:              2024-12-18T11:00:25Z
```

Row found.

---

**PaymentRepository:** Payment found. Returning to PaymentService.

---

**PaymentService:**

**Step 2 — Validate payment is refundable:**
```
payment.status: COMPLETED ✅ — can be refunded
```

Not PENDING (not yet captured) — would throw InvalidPaymentStateException.
Not REFUNDED (already refunded) — would throw InvalidPaymentStateException.

**Step 3 — Call PaymentGateway to process refund on Stripe:**
PaymentGateway, process refund for Stripe transaction
pi_3N8abcDEFGhij123, amount 20,250 THB.

---

**PaymentGateway:** *(StripePaymentGateway implementation)*

Building Stripe refund API request:
```
POST https://api.stripe.com/v1/refunds
Authorization: Bearer sk_live_miniagoda_stripe_secret_key
Content-Type: application/x-www-form-urlencoded

payment_intent=pi_3N8abcDEFGhij123
&amount=2025000
&currency=thb
&reason=requested_by_customer
```

Note: Stripe amounts are in smallest currency unit.
20,250 THB = 2,025,000 satang (1 THB = 100 satang).

Stripe API response:
```json
{
  "id":                "re_3N8xyzABCDEF456",
  "object":            "refund",
  "amount":            2025000,
  "currency":          "thb",
  "payment_intent":    "pi_3N8abcDEFGhij123",
  "status":            "pending",
  "reason":            "requested_by_customer",
  "created":           1703692800
}
```

Stripe accepted the refund request. Status is "pending" — Stripe
processes refunds asynchronously (typically 5-10 business days for
credit cards).

Returning gatewayRefundId: "re_3N8xyzABCDEF456" to PaymentService.

---

**PaymentService:**

**Step 4 — Create Refund record:**
```java
Refund refund = new Refund(
    id:              UUID.randomUUID(),
                     // "h8i9j0k1-l2m3-4n5o-6p7q-8r9s0t1u2v3w"
    paymentId:       UUID("pay-001-uuid"),
    amount:          BigDecimal.valueOf(20250),
    currencyCode:    "THB",
    status:          RefundStatus.PENDING,
    reason:          "Cancelled by guest",
    gatewayRefundId: "re_3N8xyzABCDEF456",
    createdAt:       LocalDateTime.now(),
    updatedAt:       LocalDateTime.now()
);
```

RefundRepository, save this refund.

---

**RefundRepository:**
```sql
INSERT INTO refunds (
    id, payment_id, amount, currency_code,
    status, reason, gateway_refund_id,
    created_at, updated_at
) VALUES (
    'h8i9j0k1-l2m3-4n5o-6p7q-8r9s0t1u2v3w',
    'pay-001-uuid',
    20250.00,
    'THB',
    'PENDING',
    'Cancelled by guest',
    're_3N8xyzABCDEF456',
    '2024-12-18T14:30:01Z',
    '2024-12-18T14:30:01Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(refunds table)*
```
INSERT 0 1 ✅
```

---

**RefundRepository:** Saved. Returning to PaymentService.

---

**PaymentService:**

**Step 5 — Update Payment status to REFUNDED:**
PaymentRepository, update payment pay-001-uuid status to REFUNDED.

---

**PaymentRepository:**
```sql
UPDATE payments
SET    status     = 'REFUNDED',
       updated_at = '2024-12-18T14:30:01Z'
WHERE  id = 'pay-001-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(payments table)*
```
Before: status=COMPLETED
After:  status=REFUNDED

UPDATE 1 ✅
```

---

**PaymentRepository:** Updated. Returning to PaymentService.

---

**PaymentService:** Refund initiated successfully. Returning Refund
record to BookingService.

---

**BookingService:** Refund initiated.

**Step 7 — Decrement destination booking count async:**
DestinationService, Bangkok had a cancellation. Decrement bookingCount.

---

**DestinationService:** *(on @Async thread)*

```sql
UPDATE destinations
SET    booking_count = GREATEST(booking_count - 1, 0),
       updated_at    = '2024-12-18T14:30:02Z'
WHERE  city_id = '550e8400-e29b-41d4-a716-446655440000'
AND    status  = 'ACTIVE';
```

Note: `GREATEST(booking_count - 1, 0)` — prevents negative counts
if there is any race condition.

PostgreSQL: UPDATE 1 ✅. bookingCount decremented.

*(thread returns)*

---

**BookingService:**

**Step 8 — Send cancellation notification async:**
NotificationService, send cancellation email to Shawon.

---

**NotificationService:** *(on @Async thread)*

Building Notification:
```java
Notification notification = new Notification(
    id:      UUID.randomUUID(),
    userId:  "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    hotelId: "hotel-001-uuid",
    type:    NotificationType.BOOKING_CANCELLATION,
    subject: "Your booking at Grand Hyatt Bangkok has been cancelled",
    body:    """
             Hi Ahsan Shafiq,

             Your booking has been successfully cancelled.

             Hotel:      Grand Hyatt Bangkok
             Room:       Deluxe King
             Check-in:   December 20, 2024
             Check-out:  December 25, 2024
             Booking ID: d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s

             Refund Details:
             Amount:     20,250 THB
             Method:     Credit Card
             Timeline:   5-10 business days
             Refund ID:  re_3N8xyzABCDEF456

             If you did not request this cancellation, please contact
             support immediately.

             The miniAgoda Team
             """,
    channel:    Channel.EMAIL,
    status:     NotificationStatus.PENDING,
    readStatus: NotificationReadStatus.UNREAD,
    scheduledAt: null,
    sentAt:      null,
    createdAt:   LocalDateTime.now(),
    updatedAt:   LocalDateTime.now()
);
```

NotificationRepository, save.

---

**NotificationRepository:**
```sql
INSERT INTO notifications (
    id, user_id, hotel_id, type, subject, body,
    channel, status, read_status,
    scheduled_at, sent_at, created_at, updated_at
) VALUES (
    'i9j0k1l2-m3n4-5o6p-7q8r-9s0t1u2v3w4x',
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'hotel-001-uuid',
    'BOOKING_CANCELLATION',
    'Your booking at Grand Hyatt Bangkok has been cancelled',
    'Hi Ahsan Shafiq, Your booking has been cancelled...',
    'EMAIL', 'PENDING', 'UNREAD',
    NULL, NULL,
    '2024-12-18T14:30:02Z',
    '2024-12-18T14:30:02Z'
);
```

PostgreSQL: INSERT 0 1 ✅

---

**NotificationService:** Saved. Sending via EmailGateway.

---

**EmailGateway:** *(SmtpEmailGateway)*

```
MAIL FROM: <noreply@miniagoda.com>
RCPT TO:   <shawon@example.com>
DATA
  Subject: Your booking at Grand Hyatt Bangkok has been cancelled
  [MIME message with cancellation details and refund info]
.
250 OK: Message queued as 3c4d5e6f
```

Email sent.

---

**NotificationService:** Updating status:
```sql
UPDATE notifications
SET    status    = 'SENT',
       sent_at   = '2024-12-18T14:30:03Z',
       updated_at = '2024-12-18T14:30:03Z'
WHERE  id = 'i9j0k1l2-m3n4-5o6p-7q8r-9s0t1u2v3w4x';
```

PostgreSQL: UPDATE 1 ✅. *(thread returns)*

---

**BookingService:** All steps complete. Returning cancelled Booking
to BookingController.

---

**BookingController:** Serializing 200 response:

```json
HTTP/1.1 200 OK
Content-Type: application/json
X-Request-ID: req-7g8h9i0j-1k2l-3m4n-5o6p

{
  "bookingId":         "d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s",
  "hotelName":         "Grand Hyatt Bangkok",
  "roomTypeName":      "Deluxe King",
  "checkIn":           "2024-12-20",
  "checkOut":          "2024-12-25",
  "status":            "CANCELLED",
  "cancelledAt":       "2024-12-18T14:30:00Z",
  "refund": {
    "refundId":        "h8i9j0k1-l2m3-4n5o-6p7q-8r9s0t1u2v3w",
    "amount":          20250.00,
    "currencyCode":    "THB",
    "status":          "PENDING",
    "gatewayRefundId": "re_3N8xyzABCDEF456",
    "estimatedDays":   "5-10 business days"
  }
}
```

Note: HTTP 200 OK — not 204 No Content. We return the cancelled
booking with refund details so the frontend can show them immediately.

---

**DispatcherServlet:** Writing 200. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing to bytes. Socket buffer.

---

**TCP/IP (Server OS):** Packets through Nginx.

---

**Reverse Proxy (Nginx):**
```
X-Served-By:     miniagoda-app-1
X-Response-Time: 534ms
```

534ms — includes:
- Booking lookup and update
- Write lock for inventory release
- Stripe refund API call (~200ms network round trip)
- Async tasks dispatched

---

**Load Balancer → API Gateway → Cloudflare → TLS (Client) → Browser.**

---

**Browser:** Received 200. Rendering:

```
✅ Booking Cancelled

Grand Hyatt Bangkok — Deluxe King Room
December 20–25, 2024

Your booking has been successfully cancelled.

Refund Details:
  Amount:    20,250 THB
  Method:    Credit Card
  Timeline:  5-10 business days
  Refund ID: re_3N8xyzABCDEF456

A confirmation email has been sent to shawon@example.com.

[Search New Hotels]   [View My Bookings]
```

---

**Shawon:** My booking is cancelled and my refund is on the way.
I received a cancellation email with all the details.

*(Shawon checks email)*
```
From:    noreply@miniagoda.com
Subject: Your booking at Grand Hyatt Bangkok has been cancelled

Hi Ahsan Shafiq,

Your booking has been successfully cancelled.

Hotel:      Grand Hyatt Bangkok
Room:       Deluxe King
Booking ID: d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s

Refund Details:
  Amount:    20,250 THB
  Method:    Credit Card
  Timeline:  5-10 business days
  Refund ID: re_3N8xyzABCDEF456
```

---

## Stripe Refund Webhook Flow

Stripe processes refunds asynchronously. When the refund completes
(typically 5-10 business days), Stripe sends a webhook:

```
POST /api/v1/webhooks/stripe
Stripe-Signature: t=1703692800,v1=abc123...

{
  "type": "charge.refund.updated",
  "data": {
    "object": {
      "id":     "re_3N8xyzABCDEF456",
      "status": "succeeded"
    }
  }
}
```

**StripeWebhookController** receives this and calls:
```java
paymentService.completeRefund("re_3N8xyzABCDEF456");
```

Which updates the Refund record:
```sql
UPDATE refunds
SET    status     = 'COMPLETED',
       updated_at = NOW()
WHERE  gateway_refund_id = 're_3N8xyzABCDEF456';
```

And sends a final notification to Shawon:
```
Subject: Your refund of 20,250 THB has been processed

Hi Ahsan Shafiq,
Your refund of 20,250 THB has been successfully credited
to your credit card ending in 4242.
```

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Clicks Cancel on booking |
| 2 | Browser | PATCH /bookings/{id}/cancel with JWT |
| 3 | TLS (Client) | Encrypt request |
| 4–8 | Network layers | CDN → Gateway → LB → Nginx → Tomcat |
| 9 | Spring Security | JWT verified — no DB call |
| 10 | BookingController | Extract bookingId + userId |
| 11 | BookingService | Look up booking |
| 12 | BookingRepository | SELECT booking by ID |
| 13 | PostgreSQL | Booking found — status=CONFIRMED |
| 14 | BookingService | Ownership verified ✅ |
| 15 | BookingService | Status=CONFIRMED → cancellable ✅ |
| 16 | BookingRepository | UPDATE status=CANCELLED, cancelledAt=now |
| 17 | PostgreSQL | Booking status updated |
| 18 | AvailabilityService | Acquire write lock |
| 19 | AvailabilityRepository | Verify inventory state |
| 20 | PostgreSQL | bookedRooms=2 (was 3, released) |
| 21 | AvailabilityService | Release write lock |
| 22 | PaymentService | Look up COMPLETED payment |
| 23 | PaymentRepository | SELECT payment by booking_id |
| 24 | PostgreSQL | Payment found — COMPLETED |
| 25 | PaymentGateway | POST to Stripe refund API |
| 26 | Stripe | Refund accepted — re_3N8xyzABCDEF456, PENDING |
| 27 | RefundRepository | INSERT refund — status=PENDING |
| 28 | PostgreSQL | Refund row committed |
| 29 | PaymentRepository | UPDATE payment status=REFUNDED |
| 30 | PostgreSQL | Payment status updated |
| 31 | DestinationService | DECREMENT bookingCount async |
| 32 | PostgreSQL | Destination popularity updated |
| 33 | NotificationService | Build cancellation email async |
| 34 | NotificationRepository | INSERT notification — PENDING |
| 35 | EmailGateway | SMTP send — 250 OK |
| 36 | NotificationService | UPDATE status=SENT |
| 37 | BookingController | 200 OK with refund details |
| 38 | Return path | Response through all network layers in 534ms |
| 39 | Browser | Renders cancellation confirmation with refund info |
| 40 | Shawon | Receives cancellation email |
| 41 | Stripe webhook (later) | Refund completed → status=COMPLETED |