# Scenario 1: Logged-In User, Full Search Results

**User:** Shawon (authenticated)
**Search:** Bangkok, 2 guests, 1 room, Dec 20–25, WiFi + Pool, Deluxe, King bed
**Outcome:** Enough results found — no recommendations triggered

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Builds and sends HTTP request |
| `TLS (Client)` | Client OS | Encrypts outgoing data |
| `TCP/IP (Client)` | Client OS | Breaks into packets, routes |
| `Internet` | Network | Routes packets to destination |
| `CDN (Cloudflare)` | Edge | Cache check, TLS termination |
| `API Gateway` | Infrastructure | Auth, routing, rate limiting |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `Reverse Proxy (Nginx)` | Infrastructure | HTTP parsing, routing |
| `TCP/IP (Server)` | Server OS | Receives packets, reassembles |
| `HTTP Server (Tomcat)` | Server | Parses HTTP, hands to Spring |
| `DispatcherServlet` | Spring | Routes to correct handler |
| `Spring Security Filter` | Spring | JWT validation |
| `HotelSearchController` | Application | Maps HTTP to service call |
| `HotelSearchService` | Domain | Orchestrates search logic |
| `CitySearchQuery` | Domain | Validates input |
| `SearchHistoryService` | Domain | Records search async |
| `SearchHistoryRepository` | Data | Persists search history |
| `HotelRepository` | Data | Fetches hotel data |
| `AvailabilityService` | Domain | Checks room availability |
| `AvailabilityRepository` | Data | Queries availability |
| `RatePolicy` | Domain | Resolves pricing |
| `DiscountPolicy` | Domain | Applies discounts |
| `SearchResult` | Domain | Assembles response |
| `PostgreSQL` | Database | Stores and retrieves data |

---

## The Conversation

---

**Shawon:** I want to search for hotels in Bangkok for 2 guests, 1 room,
check-in December 20th, check-out December 25th. I also want WiFi and a
Pool, a Deluxe room, and a King bed. I am logged in.
*(clicks the Search button)*

---

**Browser:** Got it Shawon. Let me build your HTTP request.

```
GET /api/v1/hotels/search
  ?cityId=550e8400-e29b-41d4-a716-446655440000
  &checkIn=2024-12-20
  &checkOut=2024-12-25
  &guestCount=2
  &rooms=1
  &amenities=WIFI,POOL
  &categories=DELUXE
  &bedTypes=KING
  &page=0
  &size=10
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaGF3b24iLCJyb2xlIjoiR1VFU1QiLCJpYXQiOjE3MDM2NTYwMDAsImV4cCI6MTcwMzc0MjQwMH0.abc123
Accept: application/json
User-Agent: Mozilla/5.0
```

Sending to api.miniagoda.com over port 443. But first — handing to TLS
to encrypt. Raw HTTP cannot travel over the internet unprotected.

---

**TLS Layer (Client):** Received raw HTTP text from Browser.

I already have an established TLS 1.3 session with api.miniagoda.com —
no new handshake needed. Using our shared session keys.

Encrypting into ciphertext:

```
[TLS Record Header]
Content Type: Application Data (23)
Version:      TLS 1.3
Length:       847 bytes

[Encrypted Payload - AES-256-GCM]
a3f8c2d1e4b9... (847 bytes of ciphertext)

[Authentication Tag - 16 bytes]
f9e2a1b3c4d5...
```

Authorization header, query parameters, everything — now opaque to
anyone intercepting. Handing encrypted bytes to TCP/IP.

---

**TCP/IP (Client OS):** Received encrypted bytes from TLS.

Breaking into packets:

```
Packet 1:
  Source IP:        192.168.1.105  (Shawon's device)
  Destination IP:   104.21.45.23   (Cloudflare Bangkok edge)
  Source Port:      54231 (ephemeral)
  Destination Port: 443
  Sequence No:      1001
  Flags:            PSH, ACK
  Payload:          bytes 1–1460 of TLS record

Packet 2:
  Source IP:        192.168.1.105
  Destination IP:   104.21.45.23
  Sequence No:      2461
  Flags:            PSH, ACK
  Payload:          remaining bytes of TLS record
```

Routing table says next hop is home router at 192.168.1.1. Sending.

---

**Internet:** Receiving packets from Shawon's router.

Routing Packet 1:
```
Hop 1: 192.168.1.1    → 203.114.5.1    (ISP gateway, Bangkok)
Hop 2: 203.114.5.1    → 103.21.244.0   (ISP backbone)
Hop 3: 103.21.244.0   → 104.21.45.23   (Cloudflare Bangkok PoP)
TTL:   52 → 51 → 50
RTT:   ~3ms
```

Both packets delivered to Cloudflare's Bangkok Point of Presence.

---

**CDN (Cloudflare):** Received packets at Bangkok edge node.

**Step 1 — TLS Termination:**
Decrypting using our shared session key:
```
Decrypted:
GET /api/v1/hotels/search?cityId=550e8400...&checkIn=2024-12-20...
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Step 2 — Cache check:**
GET /api/v1/hotels/search with Authorization header — never cache
authenticated search requests. Cache miss by design.

**Step 3 — DDoS protection:**
Shawon's IP has normal request rate. Passing.

**Step 4 — Forward to API Gateway:**
```
GET /api/v1/hotels/search?cityId=550e8400...
Host:              api-gateway.internal
X-Forwarded-For:   192.168.1.105
X-Real-IP:         192.168.1.105
CF-Ray:            8a3f2b1c4d5e6f7a-BKK
Authorization:     Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

**API Gateway:** Received request from Cloudflare.

**Step 1 — Rate limiting:**
```
Key:    rate_limit:user:shawon-uuid:search
Count:  3 requests in last 60 seconds
Limit:  30 per minute
Status: PASS ✅
```

**Step 2 — JWT expiry check:**
```
Token expiry: 2024-12-18T12:00:00Z
Now:          2024-12-18T09:30:00Z
Status:       VALID ✅
```

**Step 3 — Routing:**
```
Path:     /api/v1/hotels/search
Method:   GET
Routes to: miniagoda-app:8080
```

Forwarding with added headers:
```
X-Request-ID: req-7f3a2b1c-4d5e-6f7a-8b9c
```

---

**Load Balancer:** Received request from API Gateway.

Checking instances:
```
Instance 1: miniagoda-app-1:8080 — healthy, connections: 42
Instance 2: miniagoda-app-2:8080 — healthy, connections: 38
Instance 3: miniagoda-app-3:8080 — healthy, connections: 45

Algorithm: Least connections
Selected:  miniagoda-app-2:8080
```

Forwarding to miniagoda-app-2.

---

**Reverse Proxy (Nginx):** Received request from Load Balancer.

Matching location block:
```nginx
location /api/v1/ {
    proxy_pass         http://miniagoda-app-2:8080;
    proxy_set_header   X-Real-IP $remote_addr;
    proxy_read_timeout 30s;
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
Method:  GET
URI:     /api/v1/hotels/search
Query:   cityId=550e8400-e29b-41d4-a716-446655440000
         &checkIn=2024-12-20
         &checkOut=2024-12-25
         &guestCount=2
         &rooms=1
         &amenities=WIFI,POOL
         &categories=DELUXE
         &bedTypes=KING
         &page=0
         &size=10
Headers: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
         Accept: application/json
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

**DispatcherServlet:** Security passed. Scanning handler mappings:
```
GET /api/v1/hotels/search
→ HotelSearchController.searchByCity()
  @GetMapping("/api/v1/hotels/search")
  @PreAuthorize("permitAll()")
```

Invoking HotelSearchController.

---

**HotelSearchController:** Received request.

Mapping query parameters to CitySearchQuery:
```java
CitySearchQuery query = new CitySearchQuery(
    userId:     "shawon-uuid-here",   // from SecurityContext
    cityId:     UUID("550e8400-e29b-41d4-a716-446655440000"),
    checkIn:    LocalDate.of(2024, 12, 20),
    checkOut:   LocalDate.of(2024, 12, 25),
    guestCount: 2,
    amenities:  [WIFI, POOL],
    categories: [DELUXE],
    bedTypes:   [KING]
);
```

Calling HotelSearchService.searchByCity(query, page=0, size=10).

---

**HotelSearchService:** Received CitySearchQuery.

**Step 1 — Validate query:**
CitySearchQuery, are you valid?

---

**CitySearchQuery:** Checking:
```
cityId:               present ✅
checkIn:              2024-12-20 ✅
checkOut:             2024-12-25 ✅
checkOut after checkIn: 5 days ✅
guestCount:           2 ≥ 1 ✅
amenities:            [WIFI, POOL] — valid ✅
categories:           [DELUXE] — valid ✅
bedTypes:             [KING] — valid ✅
```

I am valid.

---

**HotelSearchService:** Good.

**Step 2 — Record search asynchronously:**
SearchHistoryService, Shawon searched Bangkok. Record in background —
do NOT block me.

---

**SearchHistoryService:** *(on a separate @Async thread)*

Building SearchHistory:
```java
SearchHistory history = new SearchHistory(
    id:        UUID.randomUUID(),
    userId:    "shawon-uuid-here",
    cityId:    "550e8400-e29b-41d4-a716-446655440000",
    checkIn:   2024-12-20,
    checkOut:  2024-12-25,
    guestCount: 2,
    status:    ACTIVE,
    createdAt: now()
);
```

SearchHistoryRepository, save this.

---

**SearchHistoryRepository:** Generating SQL:
```sql
INSERT INTO search_history (
    id, user_id, city_id, check_in, check_out,
    guest_count, status, created_at
) VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'shawon-uuid-here',
    '550e8400-e29b-41d4-a716-446655440000',
    '2024-12-20',
    '2024-12-25',
    2,
    'ACTIVE',
    '2024-12-18T09:30:01Z'
);
```

PostgreSQL, execute this.

---

**PostgreSQL:** *(search_history table)*
```
Parse:   ✅
Bind:    ✅
Execute: row inserted ✅
WAL:     write-ahead log updated ✅
Result:  INSERT 0 1
```

---

**SearchHistoryRepository:** Confirmed. Returning to SearchHistoryService.

---

**SearchHistoryService:** Saved. Async work complete.
*(thread returns to pool — main thread never waited)*

---

**HotelSearchService:** *(continuing on main thread)*

**Step 3 — Fetch ACTIVE hotels in Bangkok:**
HotelRepository, give me all ACTIVE hotels in city
550e8400-e29b-41d4-a716-446655440000.

---

**HotelRepository:** Generating SQL:
```sql
SELECT h.id, h.name, h.description, h.rating, h.status,
       h.owner_id, h.primary_image_id,
       h.phone_country_code, h.phone_number,
       a.street, a.area, a.zip_code, a.city_id,
       ha.amenity
FROM   hotels h
JOIN   addresses a         ON h.address_id   = a.id
JOIN   hotel_amenities ha  ON ha.hotel_id     = h.id
WHERE  h.city_id = '550e8400-e29b-41d4-a716-446655440000'
AND    h.status  = 'ACTIVE'
ORDER  BY h.rating DESC;
```

PostgreSQL, execute.

---

**PostgreSQL:** *(hotels table)*
```
id              | name               | rating | amenities
----------------|--------------------|--------|----------------------------------
hotel-001-uuid  | Grand Hyatt BKK    | 9.2    | WIFI,POOL,GYM,SPA,RESTAURANT
hotel-002-uuid  | Mandarin Oriental  | 9.0    | WIFI,POOL,SPA,RESTAURANT
hotel-003-uuid  | Chatrium Hotel     | 8.7    | WIFI,POOL,GYM
hotel-004-uuid  | Novotel Sukhumvit  | 8.4    | WIFI,GYM,RESTAURANT
hotel-005-uuid  | Ibis Silom         | 7.9    | WIFI

Rows: 5
```

---

**HotelRepository:** Hotels retrieved. Now fetching their room types:
```sql
SELECT rt.id, rt.hotel_id, rt.name, rt.category,
       rt.total_rooms, rt.capacity, rt.status,
       rt.primary_image_id,
       rp.id          AS rp_id,
       rp.valid_from,
       rp.valid_to,
       rp.price_per_night,
       rp.currency_code,
       dp.type        AS discount_type,
       dp.value       AS discount_value,
       dp.reason      AS discount_reason,
       rtbt.bed_type
FROM   room_types rt
JOIN   rate_policies rp       ON rp.room_type_id  = rt.id
LEFT   JOIN discount_policies dp ON dp.rate_policy_id = rp.id
JOIN   room_type_bed_types rtbt ON rtbt.room_type_id  = rt.id
WHERE  rt.hotel_id IN (
           'hotel-001-uuid','hotel-002-uuid','hotel-003-uuid',
           'hotel-004-uuid','hotel-005-uuid'
       )
AND    rt.status = 'ACTIVE';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(room_types + rate_policies + discount_policies)*
```
hotel_id        | rt_id        | name            | cat    | cap | rooms | price | discount | bed
----------------|--------------|-----------------|--------|-----|-------|-------|----------|-----
hotel-001-uuid  | rt-001-uuid  | Deluxe King     | DELUXE | 2   | 10    | 4500  | 10% off  | KING
hotel-001-uuid  | rt-002-uuid  | Grand Suite     | SUITE  | 4   | 5     | 9000  | null     | KING
hotel-002-uuid  | rt-003-uuid  | Deluxe River    | DELUXE | 2   | 5     | 6200  | null     | KING
hotel-003-uuid  | rt-004-uuid  | Deluxe City     | DELUXE | 2   | 8     | 3200  | null     | KING
hotel-004-uuid  | rt-005-uuid  | Deluxe Room     | DELUXE | 2   | 12    | 2800  | null     | KING
hotel-005-uuid  | rt-006-uuid  | Standard King   | STANDARD| 2  | 20    | 1800  | null     | KING

Rows: 6
```

---

**HotelRepository:** Mapping into Hotel objects with nested RoomType
lists. Returning 5 hotels to HotelSearchService.

---

**HotelSearchService:** Got 5 hotels.

**Step 4 — Filter by amenities (WIFI + POOL):**
```
Grand Hyatt BKK:   [WIFI, POOL, GYM, SPA, RESTAURANT] ✅
Mandarin Oriental: [WIFI, POOL, SPA, RESTAURANT]       ✅
Chatrium Hotel:    [WIFI, POOL, GYM]                   ✅
Novotel Sukhumvit: [WIFI, GYM, RESTAURANT]             ❌ missing POOL
Ibis Silom:        [WIFI]                               ❌ missing POOL
```

3 hotels remaining after amenity filter.

**Step 5 — Check room type availability:**
AvailabilityService, for hotels 001, 002, 003 — which DELUXE KING room
types are available Dec 20–25 for 2 guests?

---

**AvailabilityService:** Received request for 3 hotels.

AvailabilityRepository, count booked rooms for all room types in these
hotels for Dec 20–25.

---

**AvailabilityRepository:** Generating SQL:
```sql
SELECT   room_type_id,
         SUM(rooms) AS booked_rooms
FROM     bookings
WHERE    hotel_id IN (
             'hotel-001-uuid',
             'hotel-002-uuid',
             'hotel-003-uuid'
         )
AND      status    IN ('CONFIRMED', 'COMPLETED')
AND      check_in  < '2024-12-25'
AND      check_out > '2024-12-20'
GROUP BY room_type_id;
```

PostgreSQL, execute.

---

**PostgreSQL:** *(bookings table)*
```
room_type_id  | booked_rooms
--------------|-------------
rt-001-uuid   | 3
rt-002-uuid   | 2
rt-003-uuid   | 5
rt-004-uuid   | 1

Note: rt-005-uuid and rt-006-uuid not in results
      (belong to filtered-out hotels)
```

---

**AvailabilityRepository:** Got counts. Returning to AvailabilityService.

---

**AvailabilityService:** Comparing booked against totalRooms, applying
category and bed type filters:

```
rt-001-uuid — Grand Hyatt Deluxe King:
  totalRooms: 10, booked: 3, available: 7
  capacity:   2 ≥ guestCount(2) ✅
  category:   DELUXE ✅
  bedType:    KING ✅
  → AVAILABLE ✅

rt-002-uuid — Grand Hyatt Grand Suite:
  category: SUITE ≠ DELUXE ❌
  → filtered by category

rt-003-uuid — Mandarin Deluxe River:
  totalRooms: 5, booked: 5, available: 0
  → FULLY BOOKED ❌

rt-004-uuid — Chatrium Deluxe City:
  totalRooms: 8, booked: 1, available: 7
  capacity:   2 ≥ 2 ✅
  category:   DELUXE ✅
  bedType:    KING ✅
  → AVAILABLE ✅
```

Returning 2 available room types:
- rt-001-uuid (Grand Hyatt — Deluxe King)
- rt-004-uuid (Chatrium — Deluxe City)

Mandarin Oriental has no available Deluxe King rooms — excluded entirely.

---

**HotelSearchService:** Got 2 available room types across 2 hotels.

**Step 6 — Resolve RatePolicy for Dec 20–25:**

RatePolicy for rt-001-uuid, what is your effective price?

---

**RatePolicy (rt-001-uuid):**
```
My date ranges:
  validFrom: 2024-12-01
  validTo:   2024-12-31
  Dec 20–25 within range? ✅

pricePerNight: 4500 THB
discountPolicy: present
```

DiscountPolicy, what do you apply?

---

**DiscountPolicy (rt-001-uuid):**
```
type:   PERCENTAGE
value:  10.0
reason: "Year-End Special"

Calculation: 4500 * (1 - 10/100) = 4500 * 0.9 = 4050 THB
```

Effective price: 4050 THB/night.

---

**RatePolicy (rt-001-uuid):** Returning 4050 THB/night to
HotelSearchService.

---

**HotelSearchService:** RatePolicy for rt-004-uuid?

---

**RatePolicy (rt-004-uuid):**
```
validFrom: 2024-12-01, validTo: 2024-12-31 ✅
pricePerNight: 3200 THB
discountPolicy: null — no discount
```

Returning 3200 THB/night.

---

**HotelSearchService:** Got prices.

**Step 7 — Calculate total prices (5 nights):**
```
Grand Hyatt Deluxe King:    4050 * 5 = 20,250 THB total
Chatrium Deluxe City:       3200 * 5 = 16,000 THB total
```

**Step 8 — Build HotelSummary for each result:**
```java
HotelSummary summary1 = new HotelSummary(
    hotelId:           "hotel-001-uuid",
    name:              "Grand Hyatt Bangkok",
    address:           Address("494 Rajdamri Rd", "Pathumwan", "10330", cityId),
    rating:            9.2,
    startingFromPrice: 4050.00   // per night, after discount
);

HotelSummary summary2 = new HotelSummary(
    hotelId:           "hotel-003-uuid",
    name:              "Chatrium Hotel Bangkok",
    address:           Address("28 Charoen Krung 70", "Riverside", "10120", cityId),
    rating:            8.7,
    startingFromPrice: 3200.00
);
```

**Step 9 — Check recommendation threshold:**
```
hotels.size() = 2
threshold     = 5 (application.properties)
2 < 5 → below threshold
```

However — Shawon applied very specific filters (DELUXE + KING + POOL +
WIFI). 2 high-quality results for this specificity is a complete result
set. The recommendations would show less relevant options. In this
scenario we consider results sufficient.

*(In Scenario 3 we will see zero results and trigger full recommendation
chain.)*

**Step 10 — Sort results:**
```
Primary:   rating DESC
Secondary: startingFromPrice ASC (tiebreaker)

1. Grand Hyatt Bangkok    — rating: 9.2, from: 4050 THB
2. Chatrium Hotel Bangkok — rating: 8.7, from: 3200 THB
```

**Step 11 — Apply pagination:**
```
page: 0, size: 10, totalResults: 2
All 2 results fit on page 0
```

Assembling SearchResult.

---

**SearchResult:**
```java
new SearchResult(
    hotels: [
        HotelSummary("Grand Hyatt Bangkok",    rating=9.2, from=4050),
        HotelSummary("Chatrium Hotel Bangkok", rating=8.7, from=3200)
    ],
    suggestions:  [],
    page:         0,
    size:         10,
    totalResults: 2
)
```

Ready. Returning to HotelSearchService.

---

**HotelSearchService:** Got SearchResult. Returning to
HotelSearchController.

---

**HotelSearchController:** Received SearchResult. Serializing to JSON:

```json
HTTP/1.1 200 OK
Content-Type: application/json
X-Request-ID: req-7f3a2b1c-4d5e-6f7a-8b9c
Cache-Control: no-store

{
  "hotels": [
    {
      "hotelId": "hotel-001-uuid",
      "name": "Grand Hyatt Bangkok",
      "address": {
        "street": "494 Rajdamri Rd",
        "area": "Pathumwan",
        "zipCode": "10330",
        "cityId": "550e8400-e29b-41d4-a716-446655440000"
      },
      "rating": 9.2,
      "startingFromPrice": 4050.00
    },
    {
      "hotelId": "hotel-003-uuid",
      "name": "Chatrium Hotel Bangkok",
      "address": {
        "street": "28 Charoen Krung 70",
        "area": "Riverside",
        "zipCode": "10120",
        "cityId": "550e8400-e29b-41d4-a716-446655440000"
      },
      "rating": 8.7,
      "startingFromPrice": 3200.00
    }
  ],
  "suggestions": [],
  "page": 0,
  "size": 10,
  "totalResults": 2
}
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Writing to HttpServletResponse. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing response to bytes. Placing in
socket send buffer. TCP/IP picks up.

---

**TCP/IP (Server OS):** Breaking response into packets. Sending through
Nginx back to client.

---

**Reverse Proxy (Nginx):** Receiving response from Tomcat. Adding:
```
X-Served-By:      miniagoda-app-2
X-Response-Time:  187ms
```
Forwarding to Load Balancer.

---

**Load Balancer:** Receiving response. Decrementing connection count
for app-2 (42→41). Forwarding to API Gateway.

---

**API Gateway:** Receiving response. Logging:
```
Method:   GET /api/v1/hotels/search
User:     shawon-uuid-here
Status:   200 OK
Duration: 192ms
```
Forwarding to Cloudflare.

---

**CDN (Cloudflare):** Receiving response. Not caching (authenticated).
Logging CF-Ray: 8a3f2b1c4d5e6f7a-BKK. Encrypting with TLS and sending
back to Shawon.

---

**TLS Layer (Client):** Receiving encrypted response. Decrypting with
session key. Handing plaintext JSON to Browser.

---

**Browser:** Received and parsed JSON response. Rendering:

```
Search Results — Bangkok, Dec 20–25, 2 guests

1. ⭐ 9.2   Grand Hyatt Bangkok
            Pathumwan, Bangkok
            From 4,050 THB / night  ← Year-End Special 10% off

2. ⭐ 8.7   Chatrium Hotel Bangkok
            Riverside, Bangkok
            From 3,200 THB / night
```

---

**Shawon:** I can see the results. Grand Hyatt looks great — I will
check it out!

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Submits search |
| 2 | Browser | Builds HTTP GET with JWT |
| 3 | TLS (Client) | Encrypts — AES-256-GCM |
| 4 | TCP/IP (Client) | Splits into packets |
| 5 | Internet | Routes to Cloudflare Bangkok |
| 6 | CDN | TLS terminate, cache miss, forward |
| 7 | API Gateway | Rate limit ✅, JWT expiry ✅, route |
| 8 | Load Balancer | Selects app-2 (least connections) |
| 9 | Nginx | HTTP parse, forward to Tomcat |
| 10 | TCP/IP (Server) | Reassemble packets |
| 11 | Tomcat | Parse HTTP, hand to Spring |
| 12 | Spring Security | Verify JWT, populate SecurityContext |
| 13 | DispatcherServlet | Route to HotelSearchController |
| 14 | HotelSearchController | Map params → CitySearchQuery |
| 15 | HotelSearchService | Validate query |
| 16 | SearchHistoryService | Record search async (@Async) |
| 17 | SearchHistoryRepository | INSERT into search_history |
| 18 | PostgreSQL | Commit row |
| 19 | HotelRepository | SELECT ACTIVE hotels in Bangkok |
| 20 | PostgreSQL | Return 5 hotels + room types |
| 21 | HotelSearchService | Filter: 2 hotels lack POOL → removed |
| 22 | AvailabilityService | Check Dec 20–25 availability |
| 23 | AvailabilityRepository | SELECT booked room counts |
| 24 | PostgreSQL | Return counts per room type |
| 25 | AvailabilityService | 1 type DELUXE+KING available per hotel |
| 26 | RatePolicy | Resolve Dec price — 4050 THB (10% off) |
| 27 | DiscountPolicy | Apply Year-End Special |
| 28 | SearchResult | Assemble 2 HotelSummary records |
| 29 | HotelSearchController | Serialize to JSON, 200 OK |
| 30 | Return path | Response travels back through all layers |
| 31 | Browser | Renders 2 hotel cards |