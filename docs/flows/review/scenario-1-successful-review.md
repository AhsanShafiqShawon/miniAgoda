# Scenario 1: Successful Review Submission

**User:** Shawon (logged in, has a COMPLETED booking at Grand Hyatt Bangkok)
**Action:** Submits a detailed review with category ratings
**Outcome:** Review saved, hotel rating recalculated from all reviews

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
| `ReviewController` | Application | Maps request to service |
| `ReviewService` | Domain | Orchestrates review submission |
| `CreateReviewRequest` | Domain | Carries review data |
| `BookingRepository` | Data | Verifies booking is COMPLETED |
| `ReviewRepository` | Data | Checks for duplicates, saves review |
| `HotelRepository` | Data | Fetches all reviews for rating calc |
| `PostgreSQL` | Database | Executes all queries |
| `NotificationService` | Domain | Sends review confirmation async |
| `NotificationRepository` | Data | Persists notification |
| `EmailGateway` | Infrastructure | Sends email via SMTP |

---

## The Conversation

---

**Shawon:** I just checked out of Grand Hyatt Bangkok after my Dec 20–25
stay. I want to leave a review.
*(navigates to My Bookings → finds completed booking → clicks Write Review)*

---

**Browser:** Building HTTP POST:

```
POST /api/v1/reviews
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...abc123xyz456
Content-Type: application/json
Accept: application/json

{
  "bookingId": "d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s",
  "hotelId":   "hotel-001-uuid",
  "rating": {
    "cleanliness":   9.5,
    "facilities":    9.0,
    "location":      8.5,
    "service":       9.5,
    "valueForMoney": 7.5
  },
  "comment": "Exceptional stay at Grand Hyatt Bangkok. The Deluxe King room was immaculate and the staff were incredibly attentive. Location in Pathumwan is perfect for shopping and dining. The pool area was stunning. Only minor note is the price is on the higher side, but the quality justifies it."
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
  Source Port:      59012
  Destination Port: 443
```

Routing to Cloudflare Bangkok.

---

**Internet:** ~3ms to Cloudflare Bangkok PoP.

---

**CDN (Cloudflare):**

**TLS Termination:**
```
POST /api/v1/reviews
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Body: { "bookingId": "d4e5f6g7-...", "rating": {...}, "comment": "..." }
```

**Cache check:** POST — never cached.

**Bot protection:**
```
Rate from 192.168.1.105: 1 review in last hour
Threshold: 5 reviews per hour per IP
Status: PASS ✅
```

Forwarding to API Gateway.

---

**API Gateway:**

**Rate limiting:**
```
Key:    rate_limit:user:f47ac10b...:review
Count:  1 attempt
Limit:  10 reviews per hour
Status: PASS ✅
```

**JWT expiry:** valid ✅

**Routing:** POST /api/v1/reviews → miniagoda-app:8080

Forwarding with X-Request-ID: req-0j1k2l3m-4n5o-6p7q-8r9s

---

**Load Balancer:**
```
Selected: miniagoda-app-3:8080 (least connections)
```

---

**Reverse Proxy (Nginx):** Forwarding to Tomcat on app-3.

---

**TCP/IP (Server OS):** Reassembling. Placing in Tomcat buffer.

---

**HTTP Server (Tomcat):** Thread picked up request.
```
Method:       POST
URI:          /api/v1/reviews
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Body:         { "bookingId": "d4e5f6g7-...", ... }
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
POST /api/v1/reviews
→ ReviewController.writeReview()
  @PostMapping("/api/v1/reviews")
  @PreAuthorize("hasRole('GUEST')")
```

---

**ReviewController:** Received request.

Extracting userId from SecurityContext:
```java
String userId = SecurityContextHolder.getContext()
    .getAuthentication().getPrincipal().toString();
// "f47ac10b-58cc-4372-a567-0e02b2c3d479"
```

Deserializing to CreateReviewRequest:
```java
CreateReviewRequest request = new CreateReviewRequest(
    bookingId: UUID("d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s"),
    userId:    UUID("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
    hotelId:   UUID("hotel-001-uuid"),
    rating:    ReviewRating(
                   cleanliness:   9.5,
                   facilities:    9.0,
                   location:      8.5,
                   service:       9.5,
                   valueForMoney: 7.5
               ),
    comment:   "Exceptional stay at Grand Hyatt Bangkok..."
);
```

Running @Valid:
```
bookingId:  present ✅
userId:     present ✅
hotelId:    present ✅
rating fields: all between 1.0 and 10.0 ✅
comment:    not blank ✅
```

Calling ReviewService.writeReview(request).

---

**ReviewService:** Received CreateReviewRequest.

**Step 1 — Verify booking exists and is COMPLETED:**
BookingRepository, find booking d4e5f6g7... and check its status.

---

**BookingRepository:**
```sql
SELECT id, user_id, hotel_id, status, check_out
FROM   bookings
WHERE  id = 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(bookings table)*
```
id:        d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s
user_id:   f47ac10b-58cc-4372-a567-0e02b2c3d479
hotel_id:  hotel-001-uuid
status:    COMPLETED    ← stay has finished ✅
check_out: 2024-12-25
```

Row found.

---

**BookingRepository:** Booking found. Returning to ReviewService.

---

**ReviewService:**

**Step 2 — Verify booking ownership:**
```
booking.userId: f47ac10b-58cc-4372-a567-0e02b2c3d479
request.userId: f47ac10b-58cc-4372-a567-0e02b2c3d479
Match ✅ — Shawon owns this booking
```

**Step 3 — Verify booking status:**
```
booking.status: COMPLETED ✅ — review allowed
```

**Step 4 — Check for duplicate review:**
ReviewRepository, does a review already exist for booking
d4e5f6g7...?

---

**ReviewRepository:** Generating SQL:
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
0

No review exists for this booking yet.
```

---

**ReviewRepository:** Count = 0. No duplicate. Returning to ReviewService.

---

**ReviewService:**

**Step 5 — Build and save Review:**
```java
Review review = new Review(
    id:        UUID.randomUUID(),
               // "j0k1l2m3-n4o5-6p7q-8r9s-0t1u2v3w4x5y"
    bookingId: UUID("d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s"),
    userId:    UUID("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
    hotelId:   UUID("hotel-001-uuid"),
    rating:    ReviewRating(
                   cleanliness:   9.5,
                   facilities:    9.0,
                   location:      8.5,
                   service:       9.5,
                   valueForMoney: 7.5
               ),
    status:    ReviewStatus.ACTIVE,
    comment:   "Exceptional stay at Grand Hyatt Bangkok...",
    createdAt: LocalDateTime.now(),
    updatedAt: null
);
```

ReviewRepository, save this review.

---

**ReviewRepository:** Generating SQL:
```sql
INSERT INTO reviews (
    id, booking_id, user_id, hotel_id,
    cleanliness, facilities, location,
    service, value_for_money,
    status, comment,
    created_at, updated_at
) VALUES (
    'j0k1l2m3-n4o5-6p7q-8r9s-0t1u2v3w4x5y',
    'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s',
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'hotel-001-uuid',
    9.5, 9.0, 8.5, 9.5, 7.5,
    'ACTIVE',
    'Exceptional stay at Grand Hyatt Bangkok...',
    '2024-12-26T10:00:00Z',
    NULL
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(reviews table)*
```
Parse:   ✅
Bind:    ✅
Execute: row inserted ✅
WAL:     write-ahead log updated ✅
Result:  INSERT 0 1
```

---

**ReviewRepository:** Review saved. Returning to ReviewService.

---

**ReviewService:**

**Step 6 — Recalculate hotel rating:**

This is the most important step. Hotel rating is always derived from
all ACTIVE reviews — never set manually.

HotelRepository, give me all ACTIVE reviews for hotel-001-uuid.

---

**HotelRepository:** Generating SQL:
```sql
SELECT cleanliness, facilities, location,
       service, value_for_money
FROM   reviews
WHERE  hotel_id = 'hotel-001-uuid'
AND    status   = 'ACTIVE';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(reviews table — all ACTIVE reviews for Grand Hyatt)*
```
cleanliness | facilities | location | service | value_for_money
------------|------------|----------|---------|----------------
9.0         | 8.5        | 9.0      | 9.0     | 8.0
8.5         | 9.0        | 8.5      | 8.5     | 8.5
9.5         | 9.5        | 9.0      | 9.5     | 9.0
8.0         | 8.0        | 8.5      | 8.5     | 7.5
9.5         | 9.0        | 8.5      | 9.5     | 7.5    ← Shawon's review

5 rows returned (Shawon's review is already included
because it was just inserted above)
```

---

**HotelRepository:** Got 5 reviews. Returning to ReviewService.

---

**ReviewService:** Calculating new hotel rating:

```
Step 1 — Average each category across all 5 reviews:

cleanliness:   (9.0 + 8.5 + 9.5 + 8.0 + 9.5) / 5 = 44.5 / 5 = 8.90
facilities:    (8.5 + 9.0 + 9.5 + 8.0 + 9.0) / 5 = 44.0 / 5 = 8.80
location:      (9.0 + 8.5 + 9.0 + 8.5 + 8.5) / 5 = 43.5 / 5 = 8.70
service:       (9.0 + 8.5 + 9.5 + 8.5 + 9.5) / 5 = 45.0 / 5 = 9.00
valueForMoney: (8.0 + 8.5 + 9.0 + 7.5 + 7.5) / 5 = 40.5 / 5 = 8.10

Step 2 — Overall rating = average of all 5 category averages:

(8.90 + 8.80 + 8.70 + 9.00 + 8.10) / 5 = 43.50 / 5 = 8.70
```

New hotel rating: **8.70**

Previous hotel rating was 9.2 (from search scenario — that was older
data before these 5 reviews). Now recalculated from actual reviews.

HotelRepository, update Grand Hyatt rating to 8.70.

---

**HotelRepository:** Generating SQL:
```sql
UPDATE hotels
SET    rating     = 8.70,
       updated_at = '2024-12-26T10:00:01Z'
WHERE  id = 'hotel-001-uuid';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(hotels table)*
```
Before: rating=9.20
After:  rating=8.70

UPDATE 1 ✅

Note: The rating change will be reflected in future
search results immediately.
```

---

**HotelRepository:** Rating updated. Returning to ReviewService.

---

**ReviewService:**

**Step 7 — Send review confirmation async:**
NotificationService, confirm to Shawon that his review was submitted.

---

**NotificationService:** *(on @Async thread)*

Building Notification:
```java
Notification notification = new Notification(
    id:      UUID.randomUUID(),
    userId:  "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    hotelId: "hotel-001-uuid",
    type:    NotificationType.REVIEW_REQUEST,
    subject: "Thank you for reviewing Grand Hyatt Bangkok!",
    body:    """
             Hi Ahsan Shafiq,

             Thank you for sharing your experience!

             Your review for Grand Hyatt Bangkok has been published.

             Your Rating:
               Cleanliness:     9.5/10
               Facilities:      9.0/10
               Location:        8.5/10
               Service:         9.5/10
               Value for Money: 7.5/10

             Your comment:
             "Exceptional stay at Grand Hyatt Bangkok..."

             Your review helps other travelers make better decisions.

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
    'k1l2m3n4-o5p6-7q8r-9s0t-1u2v3w4x5y6z',
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    'hotel-001-uuid',
    'REVIEW_REQUEST',
    'Thank you for reviewing Grand Hyatt Bangkok!',
    'Hi Ahsan Shafiq, Thank you for sharing your experience!...',
    'EMAIL', 'PENDING', 'UNREAD',
    NULL, NULL,
    '2024-12-26T10:00:02Z',
    '2024-12-26T10:00:02Z'
);
```

PostgreSQL: INSERT 0 1 ✅

---

**NotificationService:** Saved as PENDING. Sending via EmailGateway.

---

**EmailGateway:**
```
MAIL FROM: <noreply@miniagoda.com>
RCPT TO:   <shawon@example.com>
DATA
  Subject: Thank you for reviewing Grand Hyatt Bangkok!
  [MIME message with review summary]
.
250 OK: Message queued as 4d5e6f7g
```

Email sent.

---

**NotificationService:** Updating status to SENT.
```sql
UPDATE notifications
SET    status    = 'SENT',
       sent_at   = '2024-12-26T10:00:03Z',
       updated_at = '2024-12-26T10:00:03Z'
WHERE  id = 'k1l2m3n4-o5p6-7q8r-9s0t-1u2v3w4x5y6z';
```

PostgreSQL: UPDATE 1 ✅. *(thread returns)*

---

**ReviewService:** Review saved, rating updated, notification dispatched.
Returning saved Review to ReviewController.

---

**ReviewController:** Received Review. Serializing:

```json
HTTP/1.1 201 Created
Content-Type: application/json
X-Request-ID: req-0j1k2l3m-4n5o-6p7q-8r9s
Location: /api/v1/reviews/j0k1l2m3-n4o5-6p7q-8r9s-0t1u2v3w4x5y

{
  "reviewId":   "j0k1l2m3-n4o5-6p7q-8r9s-0t1u2v3w4x5y",
  "bookingId":  "d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s",
  "hotelId":    "hotel-001-uuid",
  "hotelName":  "Grand Hyatt Bangkok",
  "rating": {
    "cleanliness":   9.5,
    "facilities":    9.0,
    "location":      8.5,
    "service":       9.5,
    "valueForMoney": 7.5
  },
  "comment":    "Exceptional stay at Grand Hyatt Bangkok...",
  "status":     "ACTIVE",
  "createdAt":  "2024-12-26T10:00:00Z",
  "hotelNewRating": 8.70
}
```

Note: `hotelNewRating` included in response so the frontend can
update the displayed hotel rating immediately.

---

**DispatcherServlet:** Writing 201. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing. Socket buffer.

---

**TCP/IP (Server OS):** Packets through Nginx.

---

**Reverse Proxy (Nginx):**
```
X-Served-By:     miniagoda-app-3
X-Response-Time: 287ms
```

---

**Load Balancer → API Gateway → Cloudflare → TLS (Client) → Browser.**

---

**Browser:** Received 201. Rendering:

```
✅ Review Published!

Your review of Grand Hyatt Bangkok has been published.

Your Ratings:
  Cleanliness:     ⭐ 9.5
  Facilities:      ⭐ 9.0
  Location:        ⭐ 8.5
  Service:         ⭐ 9.5
  Value for Money: ⭐ 7.5

"Exceptional stay at Grand Hyatt Bangkok. The Deluxe King
room was immaculate and the staff were incredibly attentive..."

Hotel's new rating: 8.7 ⭐

Thank you for helping other travelers!

[View Hotel]   [My Reviews]
```

---

**Shawon:** My review is published and I can see the hotel's updated
rating. I received a confirmation email too.

---

## Rating Calculation — Deep Dive

```
Why average of averages (not flat average)?

If we summed all 25 individual scores and divided by 25:
  Sum = (9.0+8.5+9.5+8.0+9.5) + (8.5+9.0+9.5+8.0+9.0) + ...
      = 44.5 + 44.0 + 43.5 + 45.0 + 40.5
      = 217.5
  Average = 217.5 / 25 = 8.70

In this case the result is identical because each review has
exactly 5 categories. If categories were optional and some
reviews had fewer scores, averaging by category first prevents
heavily-rated categories from dominating.

Our approach (average of category averages) is consistent
regardless of how many categories exist or are filled.
```

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Submits review for completed stay |
| 2 | Browser | HTTP POST with JWT and review data |
| 3 | TLS (Client) | Encrypt request |
| 4–8 | Network layers | CDN → Gateway → LB → Nginx → Tomcat |
| 9 | Spring Security | JWT verified — no DB call |
| 10 | ReviewController | Deserialize → CreateReviewRequest, @Valid |
| 11 | ReviewService | Look up booking |
| 12 | BookingRepository | SELECT booking by ID |
| 13 | PostgreSQL | Booking found — status=COMPLETED ✅ |
| 14 | ReviewService | Ownership verified ✅ |
| 15 | ReviewService | Check for duplicate review |
| 16 | ReviewRepository | SELECT COUNT(*) WHERE booking_id=? |
| 17 | PostgreSQL | Count=0 — no duplicate ✅ |
| 18 | ReviewService | Build Review — status=ACTIVE |
| 19 | ReviewRepository | INSERT review with all rating fields |
| 20 | PostgreSQL | Review row committed |
| 21 | ReviewService | Fetch all ACTIVE reviews for hotel |
| 22 | HotelRepository | SELECT reviews WHERE hotel_id=? AND status=ACTIVE |
| 23 | PostgreSQL | 5 reviews returned including Shawon's |
| 24 | ReviewService | Calculate category averages → overall 8.70 |
| 25 | HotelRepository | UPDATE hotels SET rating=8.70 |
| 26 | PostgreSQL | Hotel rating updated |
| 27 | NotificationService | Build confirmation email async |
| 28 | NotificationRepository | INSERT notification — PENDING |
| 29 | EmailGateway | SMTP send — 250 OK |
| 30 | NotificationService | UPDATE status=SENT |
| 31 | ReviewController | 201 Created with hotelNewRating |
| 32 | Return path | Response through all network layers |
| 33 | Browser | Review published, new rating shown |