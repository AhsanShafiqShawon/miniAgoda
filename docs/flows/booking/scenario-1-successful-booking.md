# Scenario 1: Successful Booking

**User:** Shawon (logged in, found Grand Hyatt in search)
**Action:** Books 1 Deluxe King room, Dec 20–25, 2 guests
**Outcome:** Booking confirmed, inventory blocked, confirmation email sent

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Builds HTTP POST |
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
| `BookingService` | Domain | Orchestrates booking logic |
| `CreateBookingRequest` | Domain | Carries booking data |
| `HotelRepository` | Data | Fetches hotel and room type |
| `AvailabilityService` | Domain | Checks and blocks availability |
| `AvailabilityRepository` | Data | Queries and updates availability |
| `RatePolicy` | Domain | Resolves price for dates |
| `DiscountPolicy` | Domain | Applies discount |
| `BookingRepository` | Data | Persists booking |
| `DestinationService` | Domain | Increments booking count async |
| `NotificationService` | Domain | Sends confirmation async |
| `NotificationRepository` | Data | Persists notification |
| `EmailGateway` | Infrastructure | Sends email via SMTP |
| `PostgreSQL` | Database | Executes all queries |

---

## The Conversation

---

**Shawon:** I found the Grand Hyatt Bangkok in my search results.
I want to book 1 Deluxe King room for Dec 20–25, 2 guests.
*(clicks Book Now)*

---

**Browser:** Building HTTP POST. Shawon is logged in — attaching JWT:

```
POST /api/v1/bookings
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
Content-Type: application/json
Accept: application/json

{
  "hotelId":    "hotel-001-uuid",
  "roomTypeId": "rt-001-uuid",
  "checkIn":    "2024-12-20",
  "checkOut":   "2024-12-25",
  "guestCount": 2,
  "rooms":      1
}
```

Handing to TLS.

---

**TLS Layer (Client):** Existing TLS 1.3 session. Encrypting with
AES-256-GCM. Handing to TCP/IP.

---

**TCP/IP (Client OS):**
```
Packet 1:
  Source IP:        192.168.1.105
  Destination IP:   104.21.45.23
  Source Port:      57890
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
POST /api/v1/bookings
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Body: { "hotelId": "hotel-001-uuid", ... }
```

**Cache check:** POST — never cached.

**Bot protection:**
```
Rate from 192.168.1.105: 1 booking attempt
Threshold: 10 per minute
Status: PASS ✅
```

Forwarding to API Gateway.

---

**API Gateway:**

**Rate limiting:**
```
Key:    rate_limit:user:f47ac10b...:booking
Count:  1 attempt
Limit:  5 bookings per minute
Status: PASS ✅
```

**JWT expiry check:**
```
exp: 2024-12-19T10:55:23Z > now: 2024-12-18T11:00:00Z ✅
```

**Routing:** POST /api/v1/bookings → miniagoda-app:8080

Forwarding with X-Request-ID: req-5e6f7g8h-9i0j-1k2l-3m4n

---

**Load Balancer:**
```
Selected: miniagoda-app-2:8080 (least connections)
```

---

**Reverse Proxy (Nginx):** Forwarding to Tomcat on app-2.

---

**TCP/IP (Server OS):** Reassembling. Placing in Tomcat buffer.

---

**HTTP Server (Tomcat):** Thread picked up request.
```
Method:       POST
URI:          /api/v1/bookings
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Body:         { "hotelId": "hotel-001-uuid", ... }
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:** JWT present.

**Extract and decode:**
```
Payload: {
  "sub":   "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "role":  "GUEST",
  "iat":   1703692523,
  "exp":   1703778923
}
Signature: verified ✅
Expiry:    valid ✅
```

**Populate SecurityContext:**
```java
new UsernamePasswordAuthenticationToken(
    "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    null,
    [ROLE_GUEST]
)
```

No database call — JWT is self-validating. Passing through.

---

**DispatcherServlet:** Routing:
```
POST /api/v1/bookings
→ BookingController.createBooking()
  @PostMapping("/api/v1/bookings")
  @PreAuthorize("hasRole('GUEST')")  ← authenticated users only
```

---

**BookingController:** Received request.

Extracting userId from SecurityContext:
```java
String userId = SecurityContextHolder.getContext()
    .getAuthentication().getPrincipal().toString();
// "f47ac10b-58cc-4372-a567-0e02b2c3d479"
```

Deserializing body to CreateBookingRequest:
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

Running @Valid:
```
hotelId:    present ✅
roomTypeId: present ✅
userId:     present ✅
rooms:      1 ≥ 1 ✅
guestCount: 2 ≥ 1 ✅
checkIn:    2024-12-20 ✅
checkOut:   2024-12-25, after checkIn ✅
```

Calling BookingService.createBooking(request).

---

**BookingService:** Received CreateBookingRequest.

**Step 1 — Validate request:**
CreateBookingRequest, are you valid?

---

**CreateBookingRequest:**
```
hotelId:    present ✅
roomTypeId: present ✅
userId:     present ✅
rooms:      1 ≥ 1 ✅
guestCount: 2 ≥ 1 ✅
checkOut > checkIn: 5 days ✅
```

Valid.

---

**BookingService:**

**Step 2 — Look up hotel and room type:**
HotelRepository, give me hotel hotel-001-uuid and its room type
rt-001-uuid.

---

**HotelRepository:**
```sql
SELECT h.id, h.name, h.status,
       rt.id AS rt_id, rt.name AS rt_name,
       rt.category, rt.total_rooms, rt.capacity,
       rt.status AS rt_status
FROM   hotels h
JOIN   room_types rt ON rt.hotel_id = h.id
WHERE  h.id  = 'hotel-001-uuid'
AND    rt.id = 'rt-001-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(hotels + room_types)*
```
hotel_id        | hotel_name       | hotel_status | rt_id       | rt_name     | capacity | total_rooms | rt_status
----------------|------------------|--------------|-------------|-------------|----------|-------------|----------
hotel-001-uuid  | Grand Hyatt BKK  | ACTIVE       | rt-001-uuid | Deluxe King | 2        | 10          | ACTIVE
```

Row found.

---

**HotelRepository:** Hotel and room type found. Returning to
BookingService.

---

**BookingService:**

**Step 3 — Validate capacity:**
```
guestCount: 2
capacity:   2
2 ≤ 2 ✅ — fits
```

**Step 4 — Check availability (TOCTOU-safe):**

This is the critical section. Between the time Shawon searched and
now, another user could have booked the last room. We must check
availability inside a write lock.

AvailabilityService, acquire write lock and check if 1 Deluxe King
room is available Dec 20–25 for 2 guests.

---

**AvailabilityService:** Acquiring write lock on AvailabilityRepository.

```java
writeLock.lock();
try {
    // All availability checks and updates happen inside this lock
    // No other booking can proceed until lock is released
```

Write lock acquired. No other booking thread can check or modify
availability until I release it.

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

**PostgreSQL:** *(bookings table — inside write lock)*
```
booked_rooms
------------
3

3 rooms currently booked for this date range.
```

---

**AvailabilityRepository:** booked_rooms = 3. Returning to
AvailabilityService.

---

**AvailabilityService:** Checking availability:
```
totalRooms:  10
bookedRooms: 3
available:   10 - 3 = 7
requested:   1

7 ≥ 1 ✅ — room available
```

Returning true to BookingService. Write lock still held.

---

**BookingService:**

Room is available inside the lock. Now resolving price before
creating the booking.

**Step 5 — Resolve RatePolicy for Dec 20–25:**
RatePolicy (rt-001-uuid), what is your effective price?

---

**RatePolicy (rt-001-uuid):**
```
Checking date ranges:
  validFrom: 2024-12-01
  validTo:   2024-12-31
  Dec 20–25 within range? ✅

pricePerNight: 4500 THB
discountPolicy: present
```

DiscountPolicy, apply your discount.

---

**DiscountPolicy (rt-001-uuid):**
```
type:   PERCENTAGE
value:  10.0
reason: "Year-End Special"

Effective price: 4500 * (1 - 10/100) = 4050 THB/night
```

Returning 4050 THB/night.

---

**RatePolicy (rt-001-uuid):** Returning 4050 THB/night to BookingService.

---

**BookingService:**

**Step 6 — Calculate total price:**
```
pricePerNight: 4050 THB
nights:        5 (Dec 20 to Dec 25)
rooms:         1
totalPrice:    4050 * 5 * 1 = 20,250 THB
currencyCode:  THB
```

**Step 7 — Create Booking record:**
```java
Booking booking = new Booking(
    id:               UUID.randomUUID(),
                      // "d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s"
    bookingGroupId:   UUID.randomUUID(),
                      // "e5f6g7h8-i9j0-1k2l-3m4n-5o6p7q8r9s0t"
    hotelId:          UUID("hotel-001-uuid"),
    roomTypeId:       UUID("rt-001-uuid"),
    userId:           UUID("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
    rooms:            1,
    guestCount:       2,
    checkIn:          LocalDate.of(2024, 12, 20),
    checkOut:         LocalDate.of(2024, 12, 25),
    status:           BookingStatus.CONFIRMED,
    totalPrice:       BigDecimal.valueOf(20250),
    currencyCode:     "THB",
    cancelledAt:      null,
    cancellationReason: null,
    createdAt:        LocalDateTime.now(),
    updatedAt:        LocalDateTime.now()
);
```

**Step 8 — Save booking:**
BookingRepository, save this booking.

---

**BookingRepository:** Generating SQL:
```sql
INSERT INTO bookings (
    id, booking_group_id, hotel_id, room_type_id, user_id,
    rooms, guest_count, check_in, check_out,
    status, total_price, currency_code,
    cancelled_at, cancellation_reason,
    created_at, updated_at
) VALUES (
    'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s',
    'e5f6g7h8-i9j0-1k2l-3m4n-5o6p7q8r9s0t',
    'hotel-001-uuid',
    'rt-001-uuid',
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    1,
    2,
    '2024-12-20',
    '2024-12-25',
    'CONFIRMED',
    20250.00,
    'THB',
    NULL,
    NULL,
    '2024-12-18T11:00:15Z',
    '2024-12-18T11:00:15Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(bookings table)*
```
Parse:   ✅
Bind:    ✅
Execute: row inserted ✅
WAL:     write-ahead log updated ✅
Result:  INSERT 0 1
```

Booking row committed.

---

**BookingRepository:** Insert confirmed. Returning to BookingService.

---

**BookingService:**

**Step 9 — Block rooms in inventory:**
AvailabilityService, block 1 room of rt-001-uuid for Dec 20–25.
We are still inside the write lock.

---

**AvailabilityService:** Still holding write lock.

AvailabilityRepository, update available count.

---

**AvailabilityRepository:**

The availability is derived from bookings table — the INSERT above
already reflected in the next query. No separate availability counter
to update. The write lock ensures no other thread sees inconsistent
state between our availability check and booking insert.

Confirming: next availability check for rt-001-uuid Dec 20–25 will
return bookedRooms = 4 (was 3, now 4 after our booking).

---

**AvailabilityService:** Inventory effectively blocked via the
booking record. Releasing write lock.

```java
} finally {
    writeLock.unlock();
}
```

Write lock released. Other booking threads may now proceed.

Returning success to BookingService.

---

**BookingService:**

**Step 10 — Increment destination booking count async:**
DestinationService, Bangkok just got a booking. Increment bookingCount
in the background.

---

**DestinationService:** *(on @Async thread)*

Looking up Bangkok destination:
```sql
UPDATE destinations
SET    booking_count = booking_count + 1,
       updated_at    = '2024-12-18T11:00:16Z'
WHERE  city_id = '550e8400-e29b-41d4-a716-446655440000'
AND    status  = 'ACTIVE';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(destinations table)*
```
UPDATE 1 ✅
bookingCount incremented — affects popularity score
```

---

**DestinationService:** Done. *(thread returns)*

---

**BookingService:**

**Step 11 — Send confirmation email async:**
NotificationService, send booking confirmation to Shawon.
Do not block me.

---

**NotificationService:** *(on @Async thread)*

Building Notification:
```java
Notification notification = new Notification(
    id:      UUID.randomUUID(),
    userId:  "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    hotelId: "hotel-001-uuid",
    type:    NotificationType.BOOKING_CONFIRMATION,
    subject: "Your booking at Grand Hyatt Bangkok is confirmed!",
    body:    """
             Hi Ahsan Shafiq,

             Your booking is confirmed!

             Hotel:      Grand Hyatt Bangkok
             Room:       Deluxe King
             Check-in:   December 20, 2024
             Check-out:  December 25, 2024
             Guests:     2
             Rooms:      1
             Total:      20,250 THB (Year-End Special 10% off)
             Booking ID: d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s

             View your booking: https://miniagoda.com/bookings/d4e5f6g7-...

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

NotificationRepository, save this.

---

**NotificationRepository:**
```sql
INSERT INTO notifications (
    id, user_id, hotel_id, type, subject, body,
    channel, status, read_status,
    scheduled_at, sent_at, created_at, updated_at
) VALUES (
    'f6g7h8i9-j0k1-2l3m-4n5o-6p7q8r9s0t1u',
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'hotel-001-uuid',
    'BOOKING_CONFIRMATION',
    'Your booking at Grand Hyatt Bangkok is confirmed!',
    'Hi Ahsan Shafiq, Your booking is confirmed!...',
    'EMAIL',
    'PENDING',
    'UNREAD',
    NULL, NULL,
    '2024-12-18T11:00:16Z',
    '2024-12-18T11:00:16Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
INSERT 0 1 ✅
```

---

**NotificationRepository:** Saved. Returning to NotificationService.

---

**NotificationService:** Record saved as PENDING. Sending via
EmailGateway.

EmailGateway, send confirmation email to shawon@example.com.

---

**EmailGateway:** *(SmtpEmailGateway)*

Connecting to SMTP:
```
Host: smtp.miniagoda.com
Port: 587
Auth: miniagoda-noreply
```

Building MIME message:
```
From:    noreply@miniagoda.com
To:      shawon@example.com
Subject: Your booking at Grand Hyatt Bangkok is confirmed!
MIME:    multipart/alternative
  text/plain: Hi Ahsan Shafiq, Your booking is confirmed!...
  text/html:  <h1>Booking Confirmed!</h1>...
```

SMTP send:
```
MAIL FROM: <noreply@miniagoda.com>
RCPT TO:   <shawon@example.com>
DATA
  [MIME message]
.
250 OK: Message queued as 2b3c4d5e
```

Email sent. Returning success.

---

**NotificationService:** Email sent. Updating status:
```sql
UPDATE notifications
SET    status    = 'SENT',
       sent_at   = '2024-12-18T11:00:17Z',
       updated_at = '2024-12-18T11:00:17Z'
WHERE  id = 'f6g7h8i9-j0k1-2l3m-4n5o-6p7q8r9s0t1u';
```

PostgreSQL: UPDATE 1 ✅

Async work complete. *(thread returns)*

---

**BookingService:** All async tasks dispatched. Returning confirmed
Booking to BookingController.

---

**BookingController:** Received confirmed Booking. Serializing:

```json
HTTP/1.1 201 Created
Content-Type: application/json
X-Request-ID: req-5e6f7g8h-9i0j-1k2l-3m4n
Location: /api/v1/bookings/d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s

{
  "bookingId":      "d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s",
  "bookingGroupId": "e5f6g7h8-i9j0-1k2l-3m4n-5o6p7q8r9s0t",
  "hotelId":        "hotel-001-uuid",
  "hotelName":      "Grand Hyatt Bangkok",
  "roomTypeId":     "rt-001-uuid",
  "roomTypeName":   "Deluxe King",
  "checkIn":        "2024-12-20",
  "checkOut":       "2024-12-25",
  "guestCount":     2,
  "rooms":          1,
  "status":         "CONFIRMED",
  "totalPrice":     20250.00,
  "currencyCode":   "THB",
  "createdAt":      "2024-12-18T11:00:15Z"
}
```

---

**DispatcherServlet:** Writing 201. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing to bytes. Socket buffer.

---

**TCP/IP (Server OS):** Packets through Nginx.

---

**Reverse Proxy (Nginx):**
```
X-Served-By:     miniagoda-app-2
X-Response-Time: 423ms
```

423ms — longer than search because of:
- Write lock acquisition
- Availability check inside lock
- Database INSERT for booking
- Rate policy resolution
- Async tasks dispatched (non-blocking)

---

**Load Balancer → API Gateway → Cloudflare → TLS (Client) → Browser.**

---

**Browser:** Received 201. Rendering:

```
✅ Booking Confirmed!

Grand Hyatt Bangkok
Deluxe King Room

Check-in:  December 20, 2024
Check-out: December 25, 2024
Guests:    2  |  Rooms: 1
Total:     20,250 THB

Booking ID: d4e5f6g7-...

A confirmation email has been sent to shawon@example.com

[View Booking]   [Search More Hotels]
```

---

**Shawon:** My booking is confirmed. I can see the details and I
received a confirmation email.

*(Shawon checks email)*
```
From:    noreply@miniagoda.com
Subject: Your booking at Grand Hyatt Bangkok is confirmed!

Hi Ahsan Shafiq,

Your booking is confirmed!

Hotel:      Grand Hyatt Bangkok
Room:       Deluxe King
Check-in:   December 20, 2024
Check-out:  December 25, 2024
Guests:     2  |  Rooms: 1
Total:      20,250 THB (Year-End Special 10% off)
Booking ID: d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s
```

---

## TOCTOU Safety Explained

```
Timeline without write lock (UNSAFE):
  Thread A: checks availability — 7 rooms free ✅
  Thread B: checks availability — 7 rooms free ✅
  Thread A: creates booking — 6 rooms free
  Thread B: creates booking — 5 rooms free
  (Both succeed even if only 1 room was left)

Timeline with write lock (SAFE):
  Thread A: acquires write lock
  Thread A: checks availability — 7 rooms free ✅
  Thread A: creates booking
  Thread A: releases lock
  Thread B: acquires write lock
  Thread B: checks availability — 6 rooms free ✅
  Thread B: creates booking
  Thread B: releases lock
  (Sequential — no double booking possible)
```

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Clicks Book Now on Grand Hyatt |
| 2 | Browser | HTTP POST with JWT and booking details |
| 3 | TLS (Client) | Encrypt request |
| 4–8 | Network layers | CDN → Gateway → LB → Nginx → Tomcat |
| 9 | Spring Security | JWT verified — no DB call |
| 10 | BookingController | userId from SecurityContext, deserialize |
| 11 | BookingService | Validate CreateBookingRequest |
| 12 | HotelRepository | Fetch hotel + room type |
| 13 | PostgreSQL | Hotel ACTIVE, RoomType ACTIVE, capacity=2 |
| 14 | BookingService | guestCount(2) ≤ capacity(2) ✅ |
| 15 | AvailabilityService | Acquire write lock |
| 16 | AvailabilityRepository | COUNT booked rooms Dec 20–25 |
| 17 | PostgreSQL | bookedRooms=3, available=7 ✅ |
| 18 | RatePolicy | Resolve Dec price — 4050 THB (10% off) |
| 19 | DiscountPolicy | Apply Year-End Special |
| 20 | BookingService | totalPrice = 4050 * 5 = 20,250 THB |
| 21 | BookingRepository | INSERT booking — status=CONFIRMED |
| 22 | PostgreSQL | Booking row committed |
| 23 | AvailabilityService | Release write lock |
| 24 | DestinationService | INCREMENT bookingCount async |
| 25 | PostgreSQL | Destination popularity updated |
| 26 | NotificationService | Build confirmation email async |
| 27 | NotificationRepository | INSERT notification — PENDING |
| 28 | EmailGateway | SMTP send — 250 OK |
| 29 | NotificationService | UPDATE status=SENT |
| 30 | BookingController | 201 Created with booking details |
| 31 | Return path | Response through all network layers |
| 32 | Browser | Renders booking confirmation |
| 33 | Shawon | Receives confirmation email |