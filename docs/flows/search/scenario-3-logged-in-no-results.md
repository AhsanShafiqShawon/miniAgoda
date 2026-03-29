# Scenario 3: Logged-In User, No Results — Full Recommendation Chain

**User:** Shawon (authenticated)
**Search:** Bangkok, 4 guests, 1 room, Dec 24–26, WiFi + Pool, Suite, King bed
**Outcome:** Zero results → relaxed dates fail → relaxed guest count succeeds

---

## Key Differences from Scenarios 1 & 2

| Aspect | Scenario 1 | Scenario 2 | Scenario 3 |
|---|---|---|---|
| User | Logged in | Anonymous | Logged in |
| Results | 2 (sufficient) | 0 (relaxed dates work) | 0 (relaxed dates fail too) |
| Recommendations | Not triggered | Relaxed dates | Relaxed dates → relaxed guests |
| History | Recorded | Not recorded | Recorded |

---

## The Cast

Same as Scenario 2, with the full recommendation chain activated.

---

## The Conversation

---

**Shawon:** I want to search for hotels in Bangkok for 4 guests, 1 room,
check-in December 24th, check-out December 26th. WiFi, Pool, Suite,
King bed. I am logged in.
*(This is Christmas Eve — extremely high demand, very specific filters)*

---

**Browser:** Building HTTP request:

```
GET /api/v1/hotels/search
  ?cityId=550e8400-e29b-41d4-a716-446655440000
  &checkIn=2024-12-24
  &checkOut=2024-12-26
  &guestCount=4
  &rooms=1
  &amenities=WIFI,POOL
  &categories=SUITE
  &bedTypes=KING
  &page=0
  &size=10
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaGF3b24iLCJyb2xlIjoiR1VFU1QiLCJpYXQiOjE3MDM2NTYwMDAsImV4cCI6MTcwMzc0MjQwMH0.abc123
Accept: application/json
```

Handing to TLS for encryption.

---

**TLS Layer (Client):** Encrypting with AES-256-GCM. Handing to TCP/IP.

---

**TCP/IP (Client OS):**
```
Packet 1:
  Source IP:        192.168.1.105  (Shawon's device)
  Destination IP:   104.21.45.23   (Cloudflare Bangkok)
  Source Port:      54899
  Destination Port: 443
  Sequence No:      1001
```

Routing to Cloudflare.

---

**Internet:** Routing packets. Bangkok → Cloudflare PoP. ~3ms.

---

**CDN (Cloudflare):** Received packets.

**Step 1 — TLS Termination:**
```
Decrypted:
GET /api/v1/hotels/search?cityId=550e8400...&checkIn=2024-12-24
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Step 2 — Cache check:**
Authorization header present — never cache authenticated requests.
Cache miss by design.

**Step 3 — Forward to API Gateway:**
```
GET /api/v1/hotels/search?...
X-Forwarded-For: 192.168.1.105
CF-Ray:          7c5d4e3f2g1h0i9j-BKK
Authorization:   Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

**API Gateway:**

**Rate limit:**
```
Key:    rate_limit:user:shawon-uuid:search
Count:  5 requests in last 60 seconds
Limit:  30 per minute
Status: PASS ✅
```

**JWT expiry:** valid ✅

**Routing:** /api/v1/hotels/search → miniagoda-app:8080

**Forward with:**
```
X-Request-ID: req-9h5c4d3e-6f7g-8a9b-0c1d
```

---

**Load Balancer:**
```
Instance 1: miniagoda-app-1:8080 — connections: 35
Instance 2: miniagoda-app-2:8080 — connections: 44
Instance 3: miniagoda-app-3:8080 — connections: 37

Selected: miniagoda-app-1:8080 (least connections)
```

---

**Reverse Proxy (Nginx):** Forwarding to Tomcat on app-1:8080.

---

**TCP/IP (Server OS):** Reassembling packets. Placing in Tomcat buffer
on app-1.

---

**HTTP Server (Tomcat):** Thread picked up request.
```
Method:  GET
URI:     /api/v1/hotels/search
Query:   cityId=550e8400...&checkIn=2024-12-24&checkOut=2024-12-26
         &guestCount=4&amenities=WIFI,POOL&categories=SUITE&bedTypes=KING
Headers: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:**

**Extract JWT:**
```
Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaGF3b24iLCJyb2xlIjoiR1VFU1QiLCJpYXQiOjE3MDM2NTYwMDAsImV4cCI6MTcwMzc0MjQwMH0.abc123
```

**Decode:**
```
{ "sub": "shawon-uuid-here", "role": "GUEST", "exp": 1703742400 }
Signature: ✅
Expiry:    valid ✅
```

**Populate SecurityContext:**
```java
new UsernamePasswordAuthenticationToken(
    "shawon-uuid-here", null,
    [ROLE_GUEST]
)
```

Passing to DispatcherServlet.

---

**DispatcherServlet:** Routing to HotelSearchController.searchByCity().

---

**HotelSearchController:** Mapping request:
```java
CitySearchQuery query = new CitySearchQuery(
    userId:     "shawon-uuid-here",
    cityId:     UUID("550e8400-e29b-41d4-a716-446655440000"),
    checkIn:    LocalDate.of(2024, 12, 24),
    checkOut:   LocalDate.of(2024, 12, 26),
    guestCount: 4,           // 4 guests — needs SUITE capacity
    amenities:  [WIFI, POOL],
    categories: [SUITE],
    bedTypes:   [KING]
);
```

Calling HotelSearchService.searchByCity(query, 0, 10).

---

**HotelSearchService:** Received CitySearchQuery.

**Step 1 — Validate:**
CitySearchQuery, are you valid?

---

**CitySearchQuery:**
```
userId:     present ✅
cityId:     present ✅
checkIn:    2024-12-24 ✅
checkOut:   2024-12-26, 2 days after checkIn ✅
guestCount: 4 ≥ 1 ✅
amenities:  [WIFI, POOL] ✅
categories: [SUITE] ✅
bedTypes:   [KING] ✅
```

Valid.

---

**HotelSearchService:**

**Step 2 — Record search async:**
SearchHistoryService, Shawon searched Bangkok. Record in background.

---

**SearchHistoryService:** *(on @Async thread)*

```java
SearchHistory history = new SearchHistory(
    id:         UUID.randomUUID(),
    userId:     "shawon-uuid-here",
    cityId:     "550e8400-e29b-41d4-a716-446655440000",
    checkIn:    2024-12-24,
    checkOut:   2024-12-26,
    guestCount: 4,
    status:     ACTIVE,
    createdAt:  now()
);
```

SearchHistoryRepository, save this.

---

**SearchHistoryRepository:**
```sql
INSERT INTO search_history (
    id, user_id, city_id, check_in, check_out,
    guest_count, status, created_at
) VALUES (
    'b2c3d4e5-f6g7-8901-bcde-f12345678901',
    'shawon-uuid-here',
    '550e8400-e29b-41d4-a716-446655440000',
    '2024-12-24',
    '2024-12-26',
    4,
    'ACTIVE',
    '2024-12-18T14:22:05Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(search_history table)*
```
INSERT 0 1 ✅
```

---

**SearchHistoryRepository:** Confirmed. Returning to SearchHistoryService.

---

**SearchHistoryService:** Saved. Async work done. *(thread returns)*

---

**HotelSearchService:** *(main thread continuing)*

**Step 3 — Fetch ACTIVE hotels in Bangkok:**
HotelRepository, give me all ACTIVE hotels in Bangkok.

---

**HotelRepository:**
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

**PostgreSQL:**
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

**HotelRepository:** Fetching room types with SUITE category:
```sql
SELECT rt.id, rt.hotel_id, rt.name, rt.category,
       rt.total_rooms, rt.capacity, rt.status,
       rp.price_per_night, rp.currency_code,
       rp.valid_from, rp.valid_to,
       dp.type AS discount_type, dp.value AS discount_value,
       rtbt.bed_type
FROM   room_types rt
JOIN   rate_policies rp       ON rp.room_type_id  = rt.id
LEFT   JOIN discount_policies dp ON dp.rate_policy_id = rp.id
JOIN   room_type_bed_types rtbt ON rtbt.room_type_id  = rt.id
WHERE  rt.hotel_id IN (
           'hotel-001-uuid','hotel-002-uuid','hotel-003-uuid',
           'hotel-004-uuid','hotel-005-uuid'
       )
AND    rt.status   = 'ACTIVE'
AND    rt.category = 'SUITE';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(only SUITE rooms)*
```
hotel_id        | rt_id        | name           | cat   | cap | rooms | price
----------------|--------------|----------------|-------|-----|-------|-------
hotel-001-uuid  | rt-002-uuid  | Grand Suite    | SUITE | 4   | 5     | 9000
hotel-002-uuid  | rt-007-uuid  | Presidential   | SUITE | 6   | 2     | 15000
hotel-002-uuid  | rt-008-uuid  | River Suite    | SUITE | 4   | 3     | 12000

Rows: 3
(Chatrium, Novotel, Ibis have no SUITE rooms)
```

---

**HotelRepository:** Mapping. Returning 3 hotels (Grand Hyatt, Mandarin,
plus filtered structure — only 2 actually have SUITE rooms with KING bed).
Returning 5 hotels with room type data to HotelSearchService.

---

**HotelSearchService:** Got hotels.

**Step 4 — Filter by amenities (WIFI + POOL):**
```
Grand Hyatt BKK:   [WIFI, POOL, GYM, SPA, RESTAURANT] ✅
Mandarin Oriental: [WIFI, POOL, SPA, RESTAURANT]       ✅
Chatrium Hotel:    [WIFI, POOL, GYM]                   ✅ but no SUITE rooms
Novotel Sukhumvit: [WIFI, GYM, RESTAURANT]             ❌ no POOL
Ibis Silom:        [WIFI]                               ❌ no POOL
```

After amenity filter: Grand Hyatt, Mandarin, Chatrium (3 hotels).
But Chatrium has no SUITE rooms — it will be filtered at room type level.

**Step 5 — Check SUITE KING availability:**
AvailabilityService, which SUITE KING room types are available Dec 24–26
for 4 guests across hotels 001, 002, 003?

---

**AvailabilityService:** AvailabilityRepository, count booked SUITE
rooms for Dec 24–26.

---

**AvailabilityRepository:**
```sql
SELECT   rt.hotel_id,
         rt.id AS room_type_id,
         rt.total_rooms,
         rt.capacity,
         COALESCE(SUM(b.rooms), 0) AS booked_rooms
FROM     room_types rt
LEFT     JOIN bookings b
             ON  b.room_type_id = rt.id
             AND b.status IN ('CONFIRMED', 'COMPLETED')
             AND b.check_in  < '2024-12-26'
             AND b.check_out > '2024-12-24'
WHERE    rt.hotel_id IN (
             'hotel-001-uuid',
             'hotel-002-uuid',
             'hotel-003-uuid'
         )
AND      rt.status   = 'ACTIVE'
AND      rt.category = 'SUITE'
GROUP BY rt.hotel_id, rt.id, rt.total_rooms, rt.capacity;
```

PostgreSQL, execute.

---

**PostgreSQL:** *(Christmas Eve — everything booked)*
```
hotel_id        | rt_id        | capacity | total | booked | available
----------------|--------------|----------|-------|--------|----------
hotel-001-uuid  | rt-002-uuid  | 4        | 5     | 5      | 0
hotel-002-uuid  | rt-007-uuid  | 6        | 2     | 2      | 0
hotel-002-uuid  | rt-008-uuid  | 4        | 3     | 3      | 0

All suites fully booked for Christmas Eve.
```

---

**AvailabilityRepository:** All 0 available. Returning to
AvailabilityService.

---

**AvailabilityService:** Checking capacity and availability:
```
rt-002-uuid — Grand Hyatt Grand Suite:
  capacity: 4 ≥ guestCount(4) ✅
  available: 0 → FULLY BOOKED ❌

rt-007-uuid — Mandarin Presidential:
  capacity: 6 ≥ 4 ✅
  available: 0 → FULLY BOOKED ❌

rt-008-uuid — Mandarin River Suite:
  capacity: 4 ≥ 4 ✅
  available: 0 → FULLY BOOKED ❌
```

0 available room types. Returning empty list.

---

**HotelSearchService:** 0 available room types. hotels list is empty.

**Step 6 — Check recommendation threshold:**
```
hotels.size() = 0
threshold     = 5
0 < 5 → trigger RecommendationService
```

foundHotelIds = [] (nothing found to exclude)

**Step 7 — Try relaxed dates first:**
RecommendationService, try ±3 days around Dec 24–26, SUITE KING,
4 guests in Bangkok.

---

**RecommendationService:** Generating date variations:
```
Original: Dec 24 – Dec 26

Variations:
  Dec 23 – Dec 25
  Dec 25 – Dec 27
  Dec 22 – Dec 24
  Dec 26 – Dec 28
  Dec 21 – Dec 23
  Dec 27 – Dec 29
```

AvailabilityService, check SUITE KING availability across these dates.

---

**AvailabilityService:** AvailabilityRepository, check all 6 date
variations for SUITE rooms in Bangkok.

---

**AvailabilityRepository:**
```sql
-- Running for each variation, showing Dec 23–25 as example:
SELECT   rt.hotel_id,
         rt.id,
         rt.total_rooms,
         rt.capacity,
         COALESCE(SUM(b.rooms), 0) AS booked_rooms,
         rt.total_rooms - COALESCE(SUM(b.rooms), 0) AS available
FROM     room_types rt
JOIN     hotels h ON h.id = rt.hotel_id
LEFT     JOIN bookings b
             ON  b.room_type_id = rt.id
             AND b.status IN ('CONFIRMED', 'COMPLETED')
             AND b.check_in  < '2024-12-25'
             AND b.check_out > '2024-12-23'
WHERE    h.city_id   = '550e8400-e29b-41d4-a716-446655440000'
AND      h.status    = 'ACTIVE'
AND      rt.status   = 'ACTIVE'
AND      rt.category = 'SUITE'
AND      rt.capacity >= 4
GROUP BY rt.hotel_id, rt.id, rt.total_rooms, rt.capacity
HAVING   rt.total_rooms - COALESCE(SUM(b.rooms), 0) > 0;
```

PostgreSQL, execute across all 6 variations.

---

**PostgreSQL:** *(Christmas period — Suites fully booked throughout)*
```
Dec 23–25: 0 rows
Dec 25–27: 0 rows
Dec 22–24: 0 rows
Dec 26–28: 0 rows (slight availability Dec 26-28 but 0 SUITE+KING+cap4)
Dec 21–23: 0 rows
Dec 27–29: 0 rows
```

No SUITE KING rooms available with capacity ≥ 4 across any ±3 day
variation around Christmas.

---

**AvailabilityRepository:** All variations returned 0. Returning to
RecommendationService.

---

**RecommendationService:** Relaxed dates found nothing. Returning
empty list to HotelSearchService.

---

**HotelSearchService:** suggestions still empty after relaxed dates.

**Step 8 — Try relaxed guest count:**
RecommendationService, try reducing guest count from 4 down to 2
(flex=2), same dates Dec 24–26, SUITE KING in Bangkok.

---

**RecommendationService:** Received guest count relaxation request.

`flex = 2` (from application.properties).

Guest count variations:
```
Original: 4 guests
Trying:   3 guests (4 - 1)
           2 guests (4 - 2)
```

AvailabilityService, check SUITE KING availability Dec 24–26 with
capacity ≥ 3, then capacity ≥ 2.

---

**AvailabilityService:** AvailabilityRepository, check SUITE
availability Dec 24–26 for capacity ≥ 3.

---

**AvailabilityRepository:**
```sql
SELECT   rt.hotel_id,
         rt.id,
         rt.total_rooms,
         rt.capacity,
         COALESCE(SUM(b.rooms), 0) AS booked_rooms,
         rt.total_rooms - COALESCE(SUM(b.rooms), 0) AS available
FROM     room_types rt
JOIN     hotels h ON h.id = rt.hotel_id
LEFT     JOIN bookings b
             ON  b.room_type_id = rt.id
             AND b.status IN ('CONFIRMED', 'COMPLETED')
             AND b.check_in  < '2024-12-26'
             AND b.check_out > '2024-12-24'
WHERE    h.city_id   = '550e8400-e29b-41d4-a716-446655440000'
AND      h.status    = 'ACTIVE'
AND      rt.status   = 'ACTIVE'
AND      rt.category = 'SUITE'
AND      rt.capacity >= 3           -- relaxed from 4 to 3
GROUP BY rt.hotel_id, rt.id, rt.total_rooms, rt.capacity
HAVING   rt.total_rooms - COALESCE(SUM(b.rooms), 0) > 0;
```

PostgreSQL, execute.

---

**PostgreSQL:** *(capacity ≥ 3 — still Christmas Eve)*
```
Still 0 rows — all SUITE rooms with capacity ≥ 3 fully booked
```

---

**AvailabilityRepository:** 0 rows for capacity ≥ 3. Trying capacity ≥ 2.

---

**AvailabilityRepository:**
```sql
-- Same query with:
AND rt.capacity >= 2    -- relaxed further
```

PostgreSQL, execute.

---

**PostgreSQL:** *(capacity ≥ 2 — smaller suites might be available)*
```
hotel_id        | rt_id        | name         | capacity | total | booked | available
----------------|--------------|--------------|----------|-------|--------|----------
hotel-001-uuid  | rt-009-uuid  | Junior Suite | 2        | 8     | 6      | 2
hotel-002-uuid  | rt-010-uuid  | Deluxe Suite | 2        | 4     | 3      | 1
hotel-003-uuid  | rt-011-uuid  | Club Suite   | 2        | 6     | 4      | 2

Rows: 3 — Junior/Deluxe/Club Suites with capacity 2 have availability
```

---

**AvailabilityRepository:** Found 3 room types with capacity ≥ 2.
Returning to RecommendationService.

---

**RecommendationService:** Got 3 results with relaxed guest count (2).

These are SUITE rooms for 2 guests — Shawon searched for 4. We are
suggesting smaller suites. The UI will surface this clearly.

Resolving RatePolicies:

**RatePolicy (rt-009-uuid — Grand Hyatt Junior Suite):**
```
validFrom: 2024-12-01, validTo: 2024-12-31 ✅
pricePerNight: 6500 THB
discountPolicy: null
```

**RatePolicy (rt-010-uuid — Mandarin Deluxe Suite):**
```
validFrom: 2024-12-01, validTo: 2024-12-31 ✅
pricePerNight: 9800 THB
discountPolicy: null
```

**RatePolicy (rt-011-uuid — Chatrium Club Suite):**
```
validFrom: 2024-12-01, validTo: 2024-12-31 ✅
pricePerNight: 4800 THB
discountPolicy: null
```

Building HotelSummary. Sorting by rating DESC, price ASC:
```
1. Grand Hyatt Bangkok    — rating: 9.2, from: 6500 THB (Junior Suite, 2 guests)
2. Mandarin Oriental      — rating: 9.0, from: 9800 THB (Deluxe Suite, 2 guests)
3. Chatrium Hotel Bangkok — rating: 8.7, from: 4800 THB (Club Suite, 2 guests)
```

Returning 3 suggestions to HotelSearchService.

---

**HotelSearchService:** Got 3 suggestions via relaxed guest count.

suggestions.size() = 3 → returning these.

**Step 9 — Assemble SearchResult:**

---

**SearchResult:**
```java
new SearchResult(
    hotels: [],    // exact match: 0
    suggestions: [
        HotelSummary("Grand Hyatt Bangkok",    rating=9.2, from=6500),
        HotelSummary("Mandarin Oriental",      rating=9.0, from=9800),
        HotelSummary("Chatrium Hotel Bangkok", rating=8.7, from=4800)
    ],
    page:         0,
    size:         10,
    totalResults: 0
)
```

Returning to HotelSearchService.

---

**HotelSearchService:** Got SearchResult. Returning to
HotelSearchController.

---

**HotelSearchController:** Serializing to JSON:

```json
HTTP/1.1 200 OK
Content-Type: application/json
X-Request-ID: req-9h5c4d3e-6f7g-8a9b-0c1d
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
      "startingFromPrice": 6500.00
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
      "startingFromPrice": 9800.00
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
      "startingFromPrice": 4800.00
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

**HTTP Server (Tomcat):** Serializing to bytes. Placing in socket buffer.

---

**TCP/IP (Server OS):** Breaking into packets. Sending through Nginx.

---

**Reverse Proxy (Nginx):** Adding headers:
```
X-Served-By:     miniagoda-app-1
X-Response-Time: 487ms
```
Forwarding to Load Balancer.

---

**Load Balancer:** Decrementing connection count for app-1 (35→34).
Forwarding to API Gateway.

---

**API Gateway:** Logging:
```
Method:   GET /api/v1/hotels/search
User:     shawon-uuid-here
Status:   200 OK
Duration: 493ms
```
Forwarding to Cloudflare.

---

**CDN (Cloudflare):** Receiving response. Authorization header was
present — not caching. Sending back to Shawon.

---

**TLS Layer (Client):** Decrypting response. Handing to Browser.

---

**Browser:** Received JSON. Parsing and rendering:

```
Search Results — Bangkok, Dec 24–26, 4 guests, Suite

No exact matches found for your search.

Similar options (smaller suites available for 2 guests):

1. ⭐ 9.2   Grand Hyatt Bangkok
            Pathumwan, Bangkok
            From 6,500 THB / night  (Junior Suite — up to 2 guests)

2. ⭐ 9.0   Mandarin Oriental Bangkok
            Bangrak, Bangkok
            From 9,800 THB / night  (Deluxe Suite — up to 2 guests)

3. ⭐ 8.7   Chatrium Hotel Bangkok
            Riverside, Bangkok
            From 4,800 THB / night  (Club Suite — up to 2 guests)

Tip: Try adjusting your dates or guest count for more options.
```

---

**Shawon:** Bangkok suites are all booked for Christmas Eve. Maybe I
should book two separate Deluxe rooms instead, or try different dates.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Searches Suite for 4 guests, Christmas Eve |
| 2 | Browser | HTTP GET with JWT |
| 3–9 | Network layers | TLS → TCP/IP → Internet → CDN → Gateway → LB → Nginx |
| 10 | Tomcat | Parse HTTP |
| 11 | Spring Security | JWT verified, SecurityContext populated |
| 12 | DispatcherServlet | Route to controller |
| 13 | HotelSearchController | guestCount=4, category=SUITE |
| 14 | HotelSearchService | Validate query |
| 15 | SearchHistoryService | Record async (@Async) — Shawon is logged in |
| 16 | SearchHistoryRepository | INSERT into search_history |
| 17 | PostgreSQL | Commit row |
| 18 | HotelRepository | SELECT ACTIVE hotels + SUITE room types |
| 19 | PostgreSQL | 3 hotels with SUITE rooms |
| 20 | HotelSearchService | Amenity filter — 3 pass |
| 21 | AvailabilityService | Check SUITE capacity ≥ 4, Dec 24–26 |
| 22 | AvailabilityRepository | SELECT booked SUITE counts |
| 23 | PostgreSQL | All 3 SUITE rooms FULLY BOOKED — Christmas Eve |
| 24 | HotelSearchService | 0 results < threshold → trigger recommendations |
| 25 | RecommendationService | Try ±3 day date variations |
| 26 | AvailabilityRepository | 6 date variation queries |
| 27 | PostgreSQL | All SUITE+KING+cap≥4 zero across all variations |
| 28 | RecommendationService | Relaxed dates failed → return empty |
| 29 | HotelSearchService | Still empty → try relaxed guest count |
| 30 | RecommendationService | Try guestCount 3, then 2 |
| 31 | AvailabilityRepository | capacity ≥ 3 → 0 results |
| 32 | AvailabilityRepository | capacity ≥ 2 → 3 Junior/Club Suites found |
| 33 | PostgreSQL | 3 smaller suites available |
| 34 | RecommendationService | Build suggestions, sort by rating |
| 35 | SearchResult | hotels=[], suggestions=[3 smaller suites] |
| 36 | HotelSearchController | Serialize to JSON, 200 OK |
| 37 | Return path | Response back through all network layers |
| 38 | CDN | Not cached (authenticated request) |
| 39 | Browser | Renders "no exact match" + 3 smaller suite suggestions |