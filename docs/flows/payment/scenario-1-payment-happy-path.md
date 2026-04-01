# Payment Happy Path: 3DS Passed, Card Charged, Booking Confirmed

**User:** Shawon (authenticated)
**Booking:** Grand Hyatt Bangkok — Deluxe King, Dec 20–25, 2 guests, 1 room
**Payment:** Visa card ending 4242, 3D Secure required by issuer
**Outcome:** Payment authorised, booking CONFIRMED, availability blocked,
notifications dispatched

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Builds and sends HTTP request |
| `TLS (Client)` | Client OS | Encrypts outgoing data |
| `TCP/IP (Client)` | Client OS | Breaks into packets, routes |
| `Internet` | Network | Routes packets to destination |
| `CDN (Cloudflare)` | Edge | TLS termination, DDoS protection |
| `API Gateway` | Infrastructure | Auth, routing, rate limiting, idempotency |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `Reverse Proxy (Nginx)` | Infrastructure | HTTP parsing, routing |
| `TCP/IP (Server)` | Server OS | Receives packets, reassembles |
| `HTTP Server (Tomcat)` | Server | Parses HTTP, hands to Spring |
| `DispatcherServlet` | Spring | Routes to correct handler |
| `Spring Security Filter` | Spring | JWT validation |
| `PaymentController` | Application | Maps HTTP to service call |
| `PaymentService` | Domain | Orchestrates payment logic |
| `BookingService` | Domain | Validates booking, ownership, amount |
| `BookingRepository` | Data | Reads / writes booking record |
| `AvailabilityService` | Domain | Final availability re-check |
| `AvailabilityRepository` | Data | Queries and updates availability |
| `PaymentRepository` | Data | Persists payment record |
| `PaymentGatewayClient` | Infrastructure | Talks to Stripe |
| `Stripe (External)` | External | Tokenises card, runs 3DS, charges |
| `NotificationService` | Domain | Dispatches email and push async |
| `PostgreSQL` | Database | Stores and retrieves all data |

---

## The Conversation

---

**Shawon:** I have reviewed my booking summary — Grand Hyatt Bangkok,
Deluxe King, December 20–25, total 20,250 THB after the Year-End 10%
discount. I will pay with my Visa ending 4242.
*(clicks "Pay Now")*

---

**Browser:** Got it Shawon. You already entered your card details in
the Stripe Elements iframe — Stripe tokenised the raw card number
client-side. It never touched miniAgoda's servers. I have a
`paymentMethodId` from Stripe.

Building HTTP request:

```
POST /api/v1/payments
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaGF3b24iLCJyb2xlIjoiR1VFU1QiLCJpYXQiOjE3MDM2NTYwMDAsImV4cCI6MTcwMzc0MjQwMH0.abc123
Content-Type: application/json
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1

{
  "bookingId":       "booking-001-uuid",
  "paymentMethodId": "pm_1OaBC2LkdIwH",
  "amount":          20250.00,
  "currency":        "THB",
  "promoCode":       null
}
```

The Idempotency-Key was generated once when Shawon landed on the
payment page. If the network drops and she retries, the same key
travels with every attempt — preventing a double charge.

Sending to api.miniagoda.com over port 443. Handing to TLS to encrypt.

---

**TLS Layer (Client):** Received raw HTTP text from Browser.

Existing TLS 1.3 session with api.miniagoda.com — no new handshake
needed. Encrypting with shared session keys:

```
[TLS Record Header]
Content Type: Application Data (23)
Version:      TLS 1.3
Length:       612 bytes

[Encrypted Payload - AES-256-GCM]
b7c3d4e2f1a9... (612 bytes of ciphertext)

[Authentication Tag - 16 bytes]
a1b2c3d4e5f6...
```

The paymentMethodId, bookingId, amount — everything — is now opaque
to any observer on the wire. Handing to TCP/IP.

---

**TCP/IP (Client OS):** Received encrypted bytes from TLS.

Breaking into packets:

```
Packet 1:
  Source IP:        192.168.1.105  (Shawon's device)
  Destination IP:   104.21.45.23   (Cloudflare Bangkok edge)
  Source Port:      54312 (ephemeral)
  Destination Port: 443
  Sequence No:      1001
  Flags:            PSH, ACK
  Payload:          bytes 1–1460 of TLS record
```

Next hop: home router at 192.168.1.1. Sending.

---

**Internet:** Routing packets from Shawon's router:

```
Hop 1: 192.168.1.1  → 203.114.5.1   (ISP gateway, Bangkok)
Hop 2: 203.114.5.1  → 103.21.244.0  (ISP backbone)
Hop 3: 103.21.244.0 → 104.21.45.23  (Cloudflare Bangkok PoP)
TTL:   52 → 51 → 50
RTT:   ~3ms
```

Packets delivered to Cloudflare Bangkok PoP.

---

**CDN (Cloudflare):** Received packets at Bangkok edge node.

**Step 1 — TLS Termination:**
```
Decrypted:
POST /api/v1/payments
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Body: { "bookingId": "booking-001-uuid", "paymentMethodId": "pm_1OaBC2LkdIwH", ... }
```

**Step 2 — Cache check:**
POST /api/v1/payments — never cached. Pass.

**Step 3 — DDoS and fraud signals:**
```
Shawon's IP:      normal rate ✅
Country match:    TH (matches billing country) ✅
Suspicious flags: none ✅
```

**Step 4 — Forward to API Gateway:**
```
POST /api/v1/payments
Host:            api-gateway.internal
X-Forwarded-For: 192.168.1.105
X-Real-IP:       192.168.1.105
CF-Ray:          9b4c3d2e5f6a7b8c-BKK
Authorization:   Bearer eyJhbGciOiJIUzI1NiJ9...
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
```

---

**API Gateway:** Received request from Cloudflare.

**Step 1 — Rate limiting:**
```
Key:    rate_limit:user:shawon-uuid:payment
Count:  1 request in last 60 seconds
Limit:  5 per minute  ← tighter limit for payment endpoints
Status: PASS ✅
```

**Step 2 — Idempotency check:**
```
Key:    idem-7f3a2b1c-4d5e-6f7a-pay1
Cache:  not seen before
Status: NEW REQUEST — proceed ✅
```

**Step 3 — JWT expiry check:**
```
Token expiry: 2024-12-18T12:00:00Z
Now:          2024-12-18T09:45:00Z
Status:       VALID ✅
```

**Step 4 — Routing:**
```
Path:      /api/v1/payments
Method:    POST
Routes to: miniagoda-app:8080
```

Adding:
```
X-Request-ID: req-9b4c3d2e-5f6a-7b8c-9d0e
```

---

**Load Balancer:** Received request from API Gateway.

```
Instance 1: miniagoda-app-1:8080 — healthy, connections: 38
Instance 2: miniagoda-app-2:8080 — healthy, connections: 41
Instance 3: miniagoda-app-3:8080 — healthy, connections: 35

Algorithm: Least connections
Selected:  miniagoda-app-3:8080
```

Forwarding to miniagoda-app-3.

---

**Reverse Proxy (Nginx):** Received request from Load Balancer.

Matching location block:
```nginx
location /api/v1/ {
    proxy_pass         http://miniagoda-app-3:8080;
    proxy_set_header   X-Real-IP $remote_addr;
    proxy_read_timeout 60s;   ← extended for payment — Stripe call expected
}
```

Forwarding to Tomcat on port 8080.

---

**TCP/IP (Server OS):** Receiving packets from Nginx on loopback
interface 127.0.0.1:8080. Reassembling into complete HTTP stream.
Placing in Tomcat socket receive buffer.

---

**HTTP Server (Tomcat):** Thread from pool picked up request from
socket buffer.

Parsing raw bytes into HTTP request object:
```
Method:  POST
URI:     /api/v1/payments
Headers: Authorization:   Bearer eyJhbGciOiJIUzI1NiJ9...
         Content-Type:    application/json
         Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
         X-Request-ID:    req-9b4c3d2e-5f6a-7b8c-9d0e
Body:    {
           "bookingId":       "booking-001-uuid",
           "paymentMethodId": "pm_1OaBC2LkdIwH",
           "amount":          20250.00,
           "currency":        "THB",
           "promoCode":       null
         }
```

Handing to Spring DispatcherServlet.

---

**DispatcherServlet:** Received HttpServletRequest from Tomcat.
Running through filter chain — Spring Security Filter is first.

---

**Spring Security Filter:** Intercepting before any controller.

**Step 1 — Extract JWT:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaGF3b24iLCJyb2xlIjoiR1VFU1QiLCJpYXQiOjE3MDM2NTYwMDAsImV4cCI6MTcwMzc0MjQwMH0.abc123
```

**Step 2 — Decode and verify:**
```
Header:    { "alg": "HS256" }
Payload:   {
             "sub":  "shawon-uuid-here",
             "role": "GUEST",
             "iat":  1703656000,
             "exp":  1703742400
           }
Signature: verified ✅
Expiry:    valid ✅
```

**Step 3 — Populate SecurityContext:**
```java
SecurityContextHolder.getContext().setAuthentication(
    new UsernamePasswordAuthenticationToken(
        "shawon-uuid-here",
        null,
        List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
    )
);
```

Shawon is authenticated. Passing to DispatcherServlet.

---

**DispatcherServlet:** Routing by path and method:
```
POST /api/v1/payments → PaymentController.initiatePayment()
```

---

**PaymentController:** Received HttpServletRequest. Deserialising body:

```java
PaymentRequest request = new PaymentRequest(
    bookingId:       "booking-001-uuid",
    paymentMethodId: "pm_1OaBC2LkdIwH",
    amount:          20250.00,
    currency:        "THB",
    promoCode:       null
);
String userId = "shawon-uuid-here"; // from SecurityContext
```

Handing to PaymentService.

---

**PaymentService:** Received PaymentRequest. Orchestrating payment.

**Step 1 — Validate booking ownership and state:**

BookingService, confirm booking-001-uuid belongs to Shawon and is
in a payable state.

---

**BookingService:** Looking up booking-001-uuid.

BookingRepository, fetch this booking.

---

**BookingRepository:**
```sql
SELECT b.*, rt.price_per_night, rt.room_type_id
FROM   bookings b
JOIN   room_types rt ON rt.room_type_id = b.room_type_id
WHERE  b.booking_id = 'booking-001-uuid'
FOR UPDATE;   ← pessimistic lock — prevents concurrent payment attempts
```

Sending to PostgreSQL.

---

**PostgreSQL:**
```
booking_id:   booking-001-uuid
user_id:      shawon-uuid-here
hotel_id:     hotel-001-uuid
room_type_id: rt-001-uuid
check_in:     2024-12-20
check_out:    2024-12-25
guests:       2
status:       PENDING_PAYMENT
total_amount: 20250.00
currency:     THB
expires_at:   2024-12-18T09:55:00Z
created_at:   2024-12-18T09:40:00Z
```

Row locked. Returning to BookingRepository.

---

**BookingRepository:** Returning Booking entity to BookingService.

---

**BookingService:**
```
Ownership:  booking.userId == shawon-uuid-here        ✅
Status:     booking.status == PENDING_PAYMENT          ✅
Amount:     booking.totalAmount == request.amount      ✅
Expiry:     expires_at 09:55:00Z > now 09:45:00Z       ✅
```

All checks passed. Returning to PaymentService.

---

**PaymentService:** Booking validated.

**Step 2 — Final availability re-check:**

AvailabilityService, confirm rt-001-uuid is still available for
Dec 20–25. Another user may have paid for the last room since
Shawon created her booking.

---

**AvailabilityService:**

AvailabilityRepository, query confirmed blocks for this room type.

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
booked: 0
```

Returning to AvailabilityRepository.

---

**AvailabilityRepository:** Returning count to AvailabilityService.

---

**AvailabilityService:**
```
totalRooms: 5   (Grand Hyatt Deluxe King inventory)
booked:     0
available:  5
Required:   1
Status:     AVAILABLE ✅
```

Returning AVAILABLE to PaymentService.

---

**PaymentService:** Room confirmed available.

**Step 3 — promoCode is null — skipping PromotionService.**

The 10% Year-End discount was already baked into booking.totalAmount
at booking creation time. No further discount to apply.

**Step 4 — Insert PENDING payment record before calling Stripe:**

PaymentRepository, persist a PENDING record now. If the server
crashes mid-charge, this record lets us reconcile with Stripe later.

---

**PaymentRepository:**
```sql
INSERT INTO payments (
    payment_id,       booking_id,        user_id,
    amount,           currency,          status,
    payment_method_id, idempotency_key,  created_at
) VALUES (
    'pay-001-uuid',   'booking-001-uuid', 'shawon-uuid-here',
    20250.00,         'THB',             'PENDING',
    'pm_1OaBC2LkdIwH', 'idem-7f3a2b1c-4d5e-6f7a-pay1', NOW()
);
```

---

**PostgreSQL:** Row inserted, payment_id = pay-001-uuid. Returning.

---

**PaymentRepository:** PENDING record saved. Returning to PaymentService.

---

**PaymentService:** PENDING record committed.

**Step 5 — Call payment gateway:**

PaymentGatewayClient, charge pm_1OaBC2LkdIwH for 20,250 THB.

---

**PaymentGatewayClient:** Building Stripe PaymentIntent request:

```json
POST https://api.stripe.com/v1/payment_intents
Authorization: Bearer sk_live_miniagoda_stripe_key
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1

{
  "amount":              2025000,
  "currency":            "thb",
  "payment_method":      "pm_1OaBC2LkdIwH",
  "confirm":             true,
  "capture_method":      "automatic",
  "return_url":          "https://miniagoda.com/payment/callback",
  "metadata": {
    "booking_id": "booking-001-uuid",
    "user_id":    "shawon-uuid-here"
  }
}
```

Note: Stripe amounts are in the smallest currency unit. THB uses
satang — 20,250 THB = 2,025,000 satang.

Sending to Stripe over TLS.

---

**Stripe (External):** Received PaymentIntent request.

**Step 1 — Token validation:**
```
pm_1OaBC2LkdIwH: valid Visa card, ending 4242 ✅
```

**Step 2 — 3D Secure evaluation:**
```
Card issuer:   Kasikorn Bank TH
Amount:        20,250 THB — above 3DS threshold
3DS required:  YES
```

Returning requires_action to PaymentGatewayClient:

```json
{
  "id":     "pi_3OaXY2LkdIwH",
  "status": "requires_action",
  "next_action": {
    "type": "use_stripe_sdk",
    "use_stripe_sdk": {
      "type":                    "three_d_secure_redirect",
      "stripe_js":               "https://hooks.stripe.com/...",
      "three_d_secure_2_source": "src_3DS_xyz"
    }
  },
  "client_secret": "pi_3OaXY2LkdIwH_secret_abc"
}
```

---

**PaymentGatewayClient:** Stripe requires 3DS. Returning
`requires_action` with `client_secret` to PaymentService.

---

**PaymentService:**

**Step 6 — Update payment record to REQUIRES_ACTION:**

```sql
UPDATE payments
SET    status      = 'REQUIRES_ACTION',
       gateway_ref = 'pi_3OaXY2LkdIwH',
       updated_at  = NOW()
WHERE  payment_id  = 'pay-001-uuid';
```

PostgreSQL: committed. Returning `requires_action` + `client_secret`
to PaymentController.

---

**PaymentController:** Serialising interim response:

```json
HTTP/1.1 202 Accepted
Content-Type: application/json
X-Request-ID: req-9b4c3d2e-5f6a-7b8c-9d0e

{
  "paymentId":    "pay-001-uuid",
  "status":       "REQUIRES_ACTION",
  "clientSecret": "pi_3OaXY2LkdIwH_secret_abc",
  "actionType":   "THREE_D_SECURE"
}
```

Response travels back through Tomcat → Nginx → Load Balancer →
API Gateway → Cloudflare → TLS → Browser.

---

**Browser:** Received 202. Stripe.js SDK intercepts `client_secret`
and opens the 3DS modal iframe served by Kasikorn Bank.

---

**Shawon:** Bank's 3DS popup appeared. Entering OTP: 482917.
*(clicks "Confirm")*

---

**Browser:** OTP submitted to Stripe and Kasikorn Bank via the
Stripe.js iframe. Stripe handles the authentication exchange — 
miniAgoda's servers are not involved in this step.

---

**Stripe (External):** Kasikorn Bank validated OTP 482917. 3DS
authentication passed.

Charging card:
```
Card:      Visa 4242
Amount:    2,025,000 satang (20,250 THB)
Result:    CAPTURED ✅
Auth code: KBK-482917-AUTH
```

Firing webhook to miniAgoda:

```json
POST https://api.miniagoda.com/api/v1/webhooks/stripe
Stripe-Signature: t=1703659500,v1=a3f8c2d1...

{
  "type": "payment_intent.succeeded",
  "data": {
    "object": {
      "id":       "pi_3OaXY2LkdIwH",
      "status":   "succeeded",
      "amount":   2025000,
      "currency": "thb",
      "metadata": {
        "booking_id": "booking-001-uuid",
        "user_id":    "shawon-uuid-here"
      },
      "charges": {
        "data": [{
          "id":                 "ch_3OaXY2LkdIwH",
          "amount_captured":    2025000,
          "authorization_code": "KBK-482917-AUTH"
        }]
      }
    }
  }
}
```

Also redirecting Shawon's browser to the return_url.

---

**PaymentController (webhook handler):** Received Stripe webhook at
`POST /api/v1/webhooks/stripe`.

**Step 1 — Verify Stripe signature:**
```
Expected: HMAC-SHA256(stripe_webhook_secret, raw_payload)
Received: a3f8c2d1...
Match:    ✅
```

Handing event to PaymentService.

---

**PaymentService:** Processing `payment_intent.succeeded`.

**Step 7 — Update payment to COMPLETED:**

```sql
UPDATE payments
SET    status             = 'COMPLETED',
       gateway_charge_id  = 'ch_3OaXY2LkdIwH',
       auth_code          = 'KBK-482917-AUTH',
       paid_at            = NOW(),
       updated_at         = NOW()
WHERE  payment_id         = 'pay-001-uuid'
  AND  status             = 'REQUIRES_ACTION';   ← guard against replay
```

---

**PostgreSQL:** Row updated. status = COMPLETED. Returning.

---

**PaymentService:** Payment COMPLETED.

**Step 8 — Confirm booking:**

BookingService, mark booking-001-uuid as CONFIRMED.

---

**BookingService:**

BookingRepository, update booking status and release the lock.

---

**BookingRepository:**
```sql
UPDATE bookings
SET    status       = 'CONFIRMED',
       payment_id   = 'pay-001-uuid',
       confirmed_at = NOW(),
       updated_at   = NOW()
WHERE  booking_id   = 'booking-001-uuid'
  AND  status       = 'PENDING_PAYMENT';
```

---

**PostgreSQL:** Row updated. Row-level lock released. Returning.

---

**BookingService:** Booking status = CONFIRMED. Returning to
PaymentService.

---

**PaymentService:** Booking confirmed.

**Step 9 — Block availability:**

AvailabilityService, mark 1 unit of rt-001-uuid as consumed for
Dec 20–25.

---

**AvailabilityService:**

AvailabilityRepository, insert the confirmed block.

---

**AvailabilityRepository:**
```sql
INSERT INTO availability_blocks (
    room_type_id,  booking_id,
    check_in,      check_out
) VALUES (
    'rt-001-uuid', 'booking-001-uuid',
    '2024-12-20',  '2024-12-25'
);
```

---

**PostgreSQL:** Row inserted. Availability reduced by 1 for Dec 20–25.
Returning.

---

**AvailabilityService:** Availability updated. Returning to
PaymentService.

---

**PaymentService:** All persistence complete.

**Step 10 — Dispatch notifications (async):**

```java
@Async
notificationService.sendPaymentConfirmation(
    userId:    "shawon-uuid-here",
    bookingId: "booking-001-uuid",
    paymentId: "pay-001-uuid"
);
```

Thread returns immediately — notification delivery does not block
the webhook response.

---

**NotificationService** *(async thread)*:

**Email — sending to shawon@email.com:**
```
Subject: Booking Confirmed — Grand Hyatt Bangkok (Ref: booking-001-uuid)

Dear Shawon,
Your booking is confirmed.

Hotel:     Grand Hyatt Bangkok
Room:      Deluxe King
Check-in:  20 December 2024
Check-out: 25 December 2024
Guests:    2
Total:     20,250 THB (incl. Year-End 10% discount)
Payment:   Visa ending 4242
```

**Push notification — to Shawon's device:**
```
Title: Booking Confirmed ✅
Body:  Grand Hyatt Bangkok · Dec 20–25 · 20,250 THB
```

Dispatching both.

---

**PaymentService:** Returning COMPLETED status to PaymentController
(webhook handler).

---

**PaymentController:** Responding to Stripe's webhook:

```json
HTTP/1.1 200 OK
```

Stripe requires a 200 within 30 seconds or it retries the webhook.

---

**Browser** *(meanwhile — redirected from Stripe's return_url)*:

Arrived at `https://miniagoda.com/payment/callback
  ?payment_intent=pi_3OaXY2LkdIwH
  &redirect_status=succeeded`

Polling for final status:

```
GET /api/v1/payments/pay-001-uuid
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

**PaymentRepository:**
```sql
SELECT status, paid_at, gateway_charge_id
FROM   payments
WHERE  payment_id = 'pay-001-uuid';
```

**PostgreSQL:** Returns `status = COMPLETED`. ✅

---

**PaymentController:** Serialising confirmation:

```json
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store

{
  "paymentId":   "pay-001-uuid",
  "bookingId":   "booking-001-uuid",
  "status":      "COMPLETED",
  "amount":      20250.00,
  "currency":    "THB",
  "paidAt":      "2024-12-18T09:48:33Z",
  "last4":       "4242",
  "cardBrand":   "VISA"
}
```

Response travels back through Nginx → Load Balancer → API Gateway →
Cloudflare → TLS → TCP/IP → Browser.

---

**Browser:** Received COMPLETED. Rendering confirmation screen:

```
✅ Payment Successful

Grand Hyatt Bangkok
Deluxe King · Dec 20–25 · 2 guests

Amount charged:  20,250 THB
Card:            Visa ····4242
Booking ref:     booking-001-uuid

A confirmation email has been sent to shawon@email.com
```

---

**Shawon:** I can see my booking confirmation. Bangkok, here I come! 🎉

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Clicks Pay Now |
| 2 | Browser | POST /api/v1/payments with paymentMethodId + idempotency key |
| 3 | TLS (Client) | Encrypts — AES-256-GCM |
| 4 | TCP/IP (Client) | Splits into packets |
| 5 | Internet | Routes to Cloudflare Bangkok |
| 6 | CDN | TLS terminate, fraud check, forward |
| 7 | API Gateway | Rate limit ✅, idempotency ✅, JWT ✅, route |
| 8 | Load Balancer | Selects app-3 (least connections) |
| 9 | Nginx | 60s timeout, forward to Tomcat |
| 10 | Tomcat | Parse HTTP, hand to Spring |
| 11 | Spring Security | Verify JWT, populate SecurityContext |
| 12 | DispatcherServlet | Route to PaymentController |
| 13 | PaymentController | Deserialise PaymentRequest |
| 14 | BookingService | Validate ownership, status, amount, expiry — row lock |
| 15 | BookingRepository | SELECT … FOR UPDATE |
| 16 | PostgreSQL | Return booking row, locked |
| 17 | AvailabilityService | Final re-check — room still available |
| 18 | AvailabilityRepository | SELECT booked count |
| 19 | PostgreSQL | Return count = 0 ✅ |
| 20 | PaymentRepository | INSERT PENDING with idempotency key |
| 21 | PostgreSQL | Commit PENDING row |
| 22 | PaymentGatewayClient | POST PaymentIntent to Stripe |
| 23 | Stripe | 3DS required → returns requires_action + client_secret |
| 24 | PaymentRepository | UPDATE status = REQUIRES_ACTION |
| 25 | PaymentController | 202 Accepted with clientSecret |
| 26 | Browser | Stripe.js opens 3DS modal (Kasikorn Bank) |
| 27 | Shawon | Enters OTP — 482917 |
| 28 | Stripe | 3DS passed → card charged → webhook fired |
| 29 | PaymentController (webhook) | Verify Stripe signature ✅ |
| 30 | PaymentRepository | UPDATE status = COMPLETED |
| 31 | PostgreSQL | Commit COMPLETED row |
| 32 | BookingRepository | UPDATE booking = CONFIRMED, lock released |
| 33 | PostgreSQL | Commit CONFIRMED row |
| 34 | AvailabilityRepository | INSERT availability_blocks |
| 35 | PostgreSQL | Commit availability row |
| 36 | NotificationService | Email + push dispatched async |
| 37 | PaymentController | 200 OK to Stripe webhook |
| 38 | Browser | Polls status endpoint — receives COMPLETED |
| 39 | Browser | Renders booking confirmation screen |
| 40 | Shawon | Sees confirmation — booking complete ✅ |