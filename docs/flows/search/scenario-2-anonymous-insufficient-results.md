# Scenario 2: Anonymous User, Insufficient Results

**User:** Anonymous (not logged in)
**Search:** Bangkok, 2 guests, 1 room, Dec 20–25, WiFi + Pool, Deluxe, King bed
**Outcome:** Results below threshold → RecommendationService triggered

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 2 |
|---|---|---|
| User | Logged in (Shawon) | Anonymous |
| JWT | Present | Absent |
| Search history | Recorded async | Not recorded |
| Results | 2 hotels (sufficient) | 1 hotel (below threshold) |
| Recommendations | Not triggered | Triggered |

---

## The Cast

Same as Scenario 1, with two additions:

| Character | Layer | Role |
|---|---|---|
| `RecommendationService` | Domain | Suggests alternative hotels |
| `AvailabilityRepository (relaxed)` | Data | Queries with relaxed criteria |

---

## The Conversation

---

**Anonymous User:** I want to search for hotels in Bangkok for 2 guests,
1 room, check-in December 20th, check-out December 25th. WiFi, Pool,
Deluxe, King bed.
*(clicks Search — not logged in)*

---

**Browser:** Building HTTP request. No Authorization header this time —
user is not logged in.

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
Accept: application/json
User-Agent: Mozilla/5.0
```

No Authorization header. Handing to TLS.

---

**TLS Layer (Client):** Encrypting request — same AES-256-GCM process
as Scenario 1. The absence of an Authorization header doesn't affect
encryption. Handing to TCP/IP.

---

**TCP/IP (Client OS):**
```
Packet 1:
  Source IP:        192.168.1.200  (anonymous device)
  Destination IP:   104.21.45.23   (Cloudflare Bangkok edge)
  Source Port:      61842 (ephemeral)
  Destination Port: 443
  Sequence No:      1001
  Flags:            PSH, ACK
```

Routing to Cloudflare Bangkok PoP.

---

**Internet:** Routing packets — same path as Scenario 1. Delivered to
Cloudflare Bangkok in ~3ms.

---

**CDN (Cloudflare):** Received packets.

**Step 1 — TLS Termination:**
Decrypting:
```
GET /api/v1/hotels/search?cityId=550e8400...
No Authorization header detected.
```

**Step 2 — Cache check:**
GET /api/v1/hotels/search with no Authorization header — no user-specific
data. Could this be cached?

```
Cache key: GET /api/v1/hotels/search?cityId=550e8400...&checkIn=2024-12-20
           &checkOut=2024-12-25&guestCount=2&amenities=WIFI,POOL
           &categories=DELUXE&bedTypes=KING

Cache TTL: 60 seconds for anonymous search results
Cache hit: MISS — first request with these parameters
```

No cache hit. Forwarding to API Gateway.

**Step 3 — Forward:**
```
GET /api/v1/hotels/search?cityId=550e8400...
Host:            api-gateway.internal
X-Forwarded-For: 192.168.1.200
CF-Ray:          9b4c3d2e5f6g7h8i-BKK
```

No Authorization header forwarded — there is none.

---

**API Gateway:** Received request.

**Step 1 — Rate limiting:**
No userId — rate limiting by IP address:
```
Key:    rate_limit:ip:192.168.1.200:search
Count:  1 request in last 60 seconds
Limit:  10 per minute (stricter for anonymous)
Status: PASS ✅
```

**Step 2 — JWT check:**
No Authorization header present. This endpoint is `permitAll()` —
anonymous access allowed. Skipping JWT validation.

**Step 3 — Routing:**
```
Path:     /api/v1/hotels/search
Method:   GET
Routes to: miniagoda-app:8080
```

Forwarding:
```
X-Request-ID: req-8g4b3c2d-5e6f-7a8b-9c0d
```

---

**Load Balancer:** Received request.
```
Instance 1: miniagoda-app-1:8080 — healthy, connections: 38
Instance 2: miniagoda-app-2:8080 — healthy, connections: 41
Instance 3: miniagoda-app-3:8080 — healthy, connections: 36

Algorithm: Least connections
Selected:  miniagoda-app-3:8080
```

Forwarding to app-3.

---

**Reverse Proxy (Nginx):** Received request. Matching location block
`/api/v1/` — forwarding to Tomcat on miniagoda-app-3:8080.

---

**TCP/IP (Server OS):** Reassembling packets. Placing in Tomcat socket
receive buffer on app-3.

---

**HTTP Server (Tomcat):** Thread picked up request.

Parsing:
```
Method:  GET
URI:     /api/v1/hotels/search
Query:   cityId=550e8400...&checkIn=2024-12-20...
Headers: Accept: application/json
         (no Authorization header)
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.
Spring Security Filter is first.

---

**Spring Security Filter:** Intercepting request.

**Step 1 — Check for JWT:**
```
Authorization header: NOT PRESENT
```

**Step 2 — Assess:**
No token. This endpoint is `@PreAuthorize("permitAll()")` — anonymous
access is allowed. I will not populate the SecurityContext with any
user. The request proceeds as an unauthenticated request.

```java
SecurityContextHolder.getContext().getAuthentication()
→ AnonymousAuthenticationToken (Spring default for unauthenticated)
  principal: "anonymousUser"
  authorities: [ROLE_ANONYMOUS]
```

Passing to DispatcherServlet.

---

**DispatcherServlet:** Security passed. Routing:
```
GET /api/v1/hotels/search
→ HotelSearchController.searchByCity()
  @PreAuthorize("permitAll()") ✅
```

Invoking HotelSearchController.

---

**HotelSearchController:** Received request.

Checking SecurityContext for userId:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
// auth is AnonymousAuthenticationToken — no real userId
UUID userId = null;   // anonymous — no userId extracted
```

Mapping to CitySearchQuery:
```java
CitySearchQuery query = new CitySearchQuery(
    userId:     null,    // anonymous — no user
    cityId:     UUID("550e8400-e29b-41d4-a716-446655440000"),
    checkIn:    LocalDate.of(2024, 12, 20),
    checkOut:   LocalDate.of(2024, 12, 25),
    guestCount: 2,
    amenities:  [WIFI, POOL],
    categories: [DELUXE],
    bedTypes:   [KING]
);
```

Calling HotelSearchService.searchByCity(query, 0, 10).

---

**HotelSearchService:** Received CitySearchQuery.

**Step 1 — Validate query:**
CitySearchQuery, are you valid?

---

**CitySearchQuery:**
```
userId:     null — anonymous, that is fine ✅
cityId:     present ✅
checkIn:    2024-12-20 ✅
checkOut:   2024-12-25, after checkIn ✅
guestCount: 2 ≥ 1 ✅
amenities:  [WIFI, POOL] ✅
categories: [DELUXE] ✅
bedTypes:   [KING] ✅
```

Valid.

---

**HotelSearchService:** Good.

**Step 2 — Record search?**
```java
if (query.userId() != null) {
    searchHistoryService.recordSearch(query.userId(), query);
}
// query.userId() is null → skip recording entirely
```

Anonymous user — no search history recorded. Moving on.

**Step 3 — Fetch ACTIVE hotels in Bangkok:**
HotelRepository, give me all ACTIVE hotels in
550e8400-e29b-41d4-a716-446655440000.

---

**HotelRepository:** Same SQL as Scenario 1:
```sql
SELECT h.id, h.name, h.description, h.rating, h.status,
       h.owner_id, h.primary_image_id,
       a.street, a.area, a.zip_code, a.city_id,
       ha.amenity
FROM   hotels h
JOIN   addresses a        ON h.address_id = a.id
JOIN   hotel_amenities ha ON ha.hotel_id  = h.id
WHERE  h.city_id = '550e8400-e29b-41d4-a716-446655440000'
AND    h.status  = 'ACTIVE'
ORDER  BY h.rating DESC;
```

PostgreSQL, execute.

---

**PostgreSQL:** *(hotels table — note: this is December 20th,
high season. Inventory is tighter than Scenario 1)*
```
id             | name               | rating | amenities
---------------|--------------------|--------|----------------------------------
hotel-001-uuid | Grand Hyatt BKK    | 9.2    | WIFI,POOL,GYM,SPA,RESTAURANT
hotel-002-uuid | Mandarin Oriental  | 9.0    | WIFI,POOL,SPA,RESTAURANT
hotel-003-uuid | Chatrium Hotel     | 8.7    | WIFI,POOL,GYM
hotel-004-uuid | Novotel Sukhumvit  | 8.4    | WIFI,GYM,RESTAURANT
hotel-005-uuid | Ibis Silom         | 7.9    | WIFI

Rows: 5
```

---

**HotelRepository:** Fetching room types (same SQL as Scenario 1).
PostgreSQL returns room type data. Mapping to Hotel objects. Returning
5 hotels.

---

**HotelSearchService:** Got 5 hotels.

**Step 4 — Filter by amenities (WIFI + POOL):**
```
Grand Hyatt BKK:   [WIFI, POOL, GYM, SPA, RESTAURANT] ✅
Mandarin Oriental: [WIFI, POOL, SPA, RESTAURANT]       ✅
Chatrium Hotel:    [WIFI, POOL, GYM]                   ✅
Novotel Sukhumvit: [WIFI, GYM, RESTAURANT]             ❌
Ibis Silom:        [WIFI]                               ❌
```

3 hotels remaining.

**Step 5 — Check room type availability:**
AvailabilityService, which DELUXE KING room types are available
Dec 20–25 for 2 guests across hotels 001, 002, 003?

---

**AvailabilityService:** AvailabilityRepository, count booked rooms.

---

**AvailabilityRepository:**
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

**PostgreSQL:** *(bookings table — high season, heavy bookings)*
```
room_type_id  | booked_rooms
--------------|-------------
rt-001-uuid   | 10          ← FULLY BOOKED (totalRooms = 10)
rt-002-uuid   | 5           ← FULLY BOOKED (totalRooms = 5)
rt-003-uuid   | 5           ← FULLY BOOKED (totalRooms = 5)
rt-004-uuid   | 8           ← FULLY BOOKED (totalRooms = 8)
```

---

**AvailabilityRepository:** All booked. Returning to AvailabilityService.

---

**AvailabilityService:** Checking availability:
```
rt-001-uuid — Grand Hyatt Deluxe King:
  totalRooms: 10, booked: 10, available: 0
  → FULLY BOOKED ❌

rt-002-uuid — Grand Hyatt Grand Suite:
  category: SUITE ≠ DELUXE ❌
  → filtered by category before availability check

rt-003-uuid — Mandarin Deluxe River:
  totalRooms: 5, booked: 5, available: 0
  → FULLY BOOKED ❌

rt-004-uuid — Chatrium Deluxe City:
  totalRooms: 8, booked: 8, available: 0
  → FULLY BOOKED ❌
```

Returning 0 available room types that match DELUXE + KING filters.

Wait — let me check if there are any DELUXE KING rooms across all
3 hotels regardless of full booking:

Actually, Chatrium (hotel-003) only has Deluxe City (rt-004) with
KING bed — all 8 booked. Grand Hyatt (hotel-001) has Deluxe King
(rt-001) — all 10 booked.

Only 1 hotel has any availability at all, and it is not DELUXE + KING:

```
hotel-001-uuid — Grand Hyatt:
  rt-002-uuid (Grand Suite, SUITE, KING): 5 total, 5 booked → FULL
```

Wait — let me check more carefully. There might be a Standard King
with availability:

Actually checking all room types across the 3 amenity-filtered hotels:
```
hotel-001-uuid: rt-001 DELUXE KING (10/10 booked), rt-002 SUITE KING (5/5 booked)
hotel-002-uuid: rt-003 DELUXE KING (5/5 booked)
hotel-003-uuid: rt-004 DELUXE KING (8/8 booked)
```

All DELUXE KING rooms across all qualifying hotels are fully booked.
Returning 0 available room types.

---

**HotelSearchService:** Got 0 available room types.

hotels list is empty.

**Step 6 — Check recommendation threshold:**
```
hotels.size() = 0
threshold     = 5
0 < 5 → BELOW THRESHOLD — trigger RecommendationService
```

First extracting found hotel IDs to exclude:
```java
List<UUID> foundHotelIds = hotels.stream()
    .map(HotelSummary::hotelId)
    .toList();
// foundHotelIds = []  (empty — no hotels found)
```

**Step 7 — Try relaxed dates first:**
RecommendationService, try ±3 days around Dec 20–25 for Bangkok,
2 guests, DELUXE KING. Exclude: [] (nothing to exclude).

---

**RecommendationService:** Received request for relaxed date suggestions.

`flexDays = 3` (from application.properties).

Generating date variations:
```
Original checkIn:  2024-12-20
Original checkOut: 2024-12-25

Variations:
  checkIn  range: Dec 17 – Dec 23 (±3 days)
  checkOut range: Dec 22 – Dec 28 (±3 days)

Trying combinations (prioritizing closest to original):
  1. Dec 19 – Dec 24
  2. Dec 21 – Dec 26
  3. Dec 18 – Dec 23
  4. Dec 22 – Dec 27
  5. Dec 17 – Dec 22
  6. Dec 23 – Dec 28
```

AvailabilityService, check availability for these date ranges in
Bangkok (city 550e8400...) with capacity ≥ 2. No hotel exclusions.

---

**AvailabilityService:** Received relaxed date availability request.

AvailabilityRepository, for each date variation — which DELUXE KING
room types have availability in Bangkok hotels?

---

**AvailabilityRepository:**
```sql
-- Running for each date variation
-- Showing Dec 19–24 as example:
SELECT   rt.hotel_id,
         rt.id AS room_type_id,
         rt.total_rooms,
         COALESCE(SUM(b.rooms), 0) AS booked_rooms,
         rt.total_rooms - COALESCE(SUM(b.rooms), 0) AS available
FROM     room_types rt
JOIN     hotels h ON h.id = rt.hotel_id
LEFT     JOIN bookings b
             ON  b.room_type_id = rt.id
             AND b.status IN ('CONFIRMED', 'COMPLETED')
             AND b.check_in  < '2024-12-24'
             AND b.check_out > '2024-12-19'
WHERE    h.city_id   = '550e8400-e29b-41d4-a716-446655440000'
AND      h.status    = 'ACTIVE'
AND      rt.status   = 'ACTIVE'
AND      rt.category = 'DELUXE'
AND      rt.capacity >= 2
GROUP BY rt.hotel_id, rt.id, rt.total_rooms
HAVING   rt.total_rooms - COALESCE(SUM(b.rooms), 0) > 0;
```

PostgreSQL, execute for each variation.

---

**PostgreSQL:** *(running across 6 date variations)*

```
Dec 19–24:
  rt-001-uuid (Grand Hyatt Deluxe King):  10 total, 9 booked, 1 available ✅
  rt-004-uuid (Chatrium Deluxe City):      8 total, 6 booked, 2 available ✅

Dec 21–26:
  rt-004-uuid (Chatrium Deluxe City):      8 total, 5 booked, 3 available ✅

Dec 18–23:
  No DELUXE KING rooms available

Dec 22–27:
  rt-003-uuid (Mandarin Deluxe River):     5 total, 3 booked, 2 available ✅

Dec 17–22:
  No DELUXE KING rooms available

Dec 23–28:
  rt-001-uuid (Grand Hyatt Deluxe King):  10 total, 4 booked, 6 available ✅
  rt-003-uuid (Mandarin Deluxe River):     5 total, 2 booked, 3 available ✅
```

---

**AvailabilityRepository:** Got availability across date variations.
Returning to RecommendationService.

---

**RecommendationService:** Got results. Deduplicating hotels across
date variations:

```
Unique hotels found:
  hotel-001-uuid (Grand Hyatt BKK)    — available Dec 19–24 and Dec 23–28
  hotel-003-uuid (Chatrium Hotel)     — available Dec 19–24 and Dec 21–26
  hotel-002-uuid (Mandarin Oriental)  — available Dec 22–27 and Dec 23–28
```

Resolving RatePolicy for each — same process as Scenario 1.

```
Grand Hyatt Deluxe King:   4050 THB/night (10% Year-End Special)
Chatrium Deluxe City:      3200 THB/night (no discount)
Mandarin Deluxe River:     6200 THB/night (no discount)
```

Building HotelSummary for each. Sorting: rating DESC, price ASC:
```
1. Grand Hyatt Bangkok    — rating: 9.2, from: 4050 THB
2. Mandarin Oriental      — rating: 9.0, from: 6200 THB
3. Chatrium Hotel Bangkok — rating: 8.7, from: 3200 THB
```

Applying pagination (page=0, size=5 for recommendations).

Returning 3 suggestions to HotelSearchService.

---

**HotelSearchService:** Got 3 suggestions from RecommendationService.

suggestions.size() = 3 → enough to show. No need to try relaxed
guest count.

**Step 8 — Assemble SearchResult:**

---

**SearchResult:**
```java
new SearchResult(
    hotels: [],    // empty — no exact match found
    suggestions: [
        HotelSummary("Grand Hyatt Bangkok",    rating=9.2, from=4050),
        HotelSummary("Mandarin Oriental",      rating=9.0, from=6200),
        HotelSummary("Chatrium Hotel Bangkok", rating=8.7, from=3200)
    ],
    page:         0,
    size:         10,
    totalResults: 0    // exact results = 0
)
```

Returning to HotelSearchService.

---

**HotelSearchService:** Got SearchResult. Returning to
HotelSearchController.

---

**HotelSearchController:** Received SearchResult. Serializing to JSON:

```json
HTTP/1.1 200 OK
Content-Type: application/json
X-Request-ID: req-8g4b3c2d-5e6f-7a8b-9c0d
Cache-Control: no-store

{
  "hotels": [],
  "suggestions": [
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
      "hotelId": "hotel-002-uuid",
      "name": "Mandarin Oriental Bangkok",
      "address": {
        "street": "48 Oriental Ave",
        "area": "Bangrak",
        "zipCode": "10500",
        "cityId": "550e8400-e29b-41d4-a716-446655440000"
      },
      "rating": 9.0,
      "startingFromPrice": 6200.00
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
  "page": 0,
  "size": 10,
  "totalResults": 0
}
```

---

**DispatcherServlet:** Writing to HttpServletResponse. Handing to Tomcat.

---

**HTTP Server (Tomcat):** Serializing response to bytes. Placing in
socket send buffer.

---

**TCP/IP (Server OS):** Breaking into packets. Sending through Nginx.

---

**Reverse Proxy (Nginx):** Receiving response. Adding:
```
X-Served-By:     miniagoda-app-3
X-Response-Time: 312ms
```
Forwarding to Load Balancer.

---

**Load Balancer:** Decrementing connection count for app-3 (36→35).
Forwarding to API Gateway.

---

**API Gateway:** Logging:
```
Method:   GET /api/v1/hotels/search
User:     anonymous
Status:   200 OK
Duration: 318ms
```
Forwarding to Cloudflare.

---

**CDN (Cloudflare):** Receiving response.

This time — no Authorization header on the original request. Should I
cache this?

```
Cache key: GET /api/v1/hotels/search?cityId=550e8400...&checkIn=2024-12-20
           &checkOut=2024-12-25&guestCount=2&amenities=WIFI,POOL
           &categories=DELUXE&bedTypes=KING
Cache TTL: 60 seconds
Decision:  CACHE ✅ — anonymous request, same parameters = same result
           for 60 seconds
```

Caching response. Any other anonymous user searching these exact
parameters in the next 60 seconds gets this cached response instantly
without hitting the backend.

Sending response back to anonymous user.

---

**TLS Layer (Client):** Decrypting response. Handing to Browser.

---

**Browser:** Received JSON. Parsing and rendering:

```
Search Results — Bangkok, Dec 20–25, 2 guests

No exact matches found for your dates.

You might also like:

1. ⭐ 9.2   Grand Hyatt Bangkok
            Pathumwan, Bangkok
            From 4,050 THB / night
            (Available nearby dates)

2. ⭐ 9.0   Mandarin Oriental Bangkok
            Bangrak, Bangkok
            From 6,200 THB / night
            (Available nearby dates)

3. ⭐ 8.7   Chatrium Hotel Bangkok
            Riverside, Bangkok
            From 3,200 THB / night
            (Available nearby dates)
```

---

**Anonymous User:** Hmm, no exact matches. But Grand Hyatt looks
interesting — let me sign up and check availability for nearby dates.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Anonymous | Submits search, no login |
| 2 | Browser | Builds HTTP GET — no Authorization header |
| 3 | TLS (Client) | Encrypts request |
| 4 | TCP/IP (Client) | Splits into packets |
| 5 | Internet | Routes to Cloudflare Bangkok |
| 6 | CDN | TLS terminate, cache MISS (first request) |
| 7 | API Gateway | Rate limit by IP ✅, no JWT to check |
| 8 | Load Balancer | Selects app-3 (least connections) |
| 9 | Nginx | HTTP parse, forward to Tomcat |
| 10 | Tomcat | Parse HTTP, hand to Spring |
| 11 | Spring Security | No JWT → AnonymousAuthenticationToken |
| 12 | DispatcherServlet | Route to HotelSearchController |
| 13 | HotelSearchController | userId = null (anonymous) |
| 14 | HotelSearchService | Validate query |
| 15 | HotelSearchService | userId null → skip search history |
| 16 | HotelRepository | SELECT ACTIVE hotels in Bangkok |
| 17 | PostgreSQL | Return 5 hotels + room types |
| 18 | HotelSearchService | Filter: 2 hotels lack POOL → removed |
| 19 | AvailabilityService | Check Dec 20–25 availability |
| 20 | AvailabilityRepository | SELECT booked counts |
| 21 | PostgreSQL | All DELUXE KING fully booked |
| 22 | HotelSearchService | 0 results < threshold(5) → trigger recommendations |
| 23 | RecommendationService | Try ±3 day date variations |
| 24 | AvailabilityRepository | SELECT across 6 date variations |
| 25 | PostgreSQL | Found availability on Dec 19–24, 21–26, 22–27, 23–28 |
| 26 | RecommendationService | Deduplicate 3 hotels, sort by rating |
| 27 | SearchResult | hotels=[], suggestions=[3 hotels] |
| 28 | HotelSearchController | Serialize to JSON, 200 OK |
| 29 | Return path | Response travels back through all layers |
| 30 | CDN | CACHE response for 60s (anonymous request) |
| 31 | Browser | Renders "no exact matches" + 3 suggestions |