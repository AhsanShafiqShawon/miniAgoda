# Scenario 1: Successful Hotel Addition

**User:** Karim (hotel owner, role=HOTEL_ADMIN)
**Action:** Adds a new hotel in Bangkok with one room type and a primary image
**Outcome:** Hotel created PENDING, room type added, inventory initialized,
             image uploaded and confirmed

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Karim` | Client | Hotel owner |
| `Browser` | Client | Builds HTTP requests |
| `TLS (Client)` | Client OS | Encrypts requests |
| `TCP/IP (Client)` | Client OS | Routes packets |
| `Internet` | Network | Routes to server |
| `CDN (Cloudflare)` | Edge | TLS termination |
| `API Gateway` | Infrastructure | Auth, routing, rate limiting |
| `Load Balancer` | Infrastructure | Distributes traffic |
| `Reverse Proxy (Nginx)` | Infrastructure | HTTP parsing |
| `TCP/IP (Server)` | Server OS | Reassembles packets |
| `HTTP Server (Tomcat)` | Server | Parses HTTP |
| `DispatcherServlet` | Spring | Routes to handler |
| `Spring Security Filter` | Spring | JWT validation, role check |
| `HotelController` | Application | Maps request to HotelService |
| `HotelService` | Domain | Creates hotel |
| `AddHotelRequest` | Domain | Carries hotel data |
| `UserRepository` | Data | Verifies owner has HOTEL_ADMIN role |
| `CityRepository` | Data | Verifies cityId exists |
| `HotelRepository` | Data | Saves hotel |
| `RoomTypeController` | Application | Maps request to RoomTypeService |
| `RoomTypeService` | Domain | Adds room type, initializes inventory |
| `AddRoomTypeRequest` | Domain | Carries room type data |
| `AvailabilityService` | Domain | Initializes inventory for room type |
| `AvailabilityRepository` | Data | Stores inventory record |
| `ImageController` | Application | Maps request to ImageService |
| `ImageService` | Domain | Uploads and confirms image |
| `ImageUploadRequest` | Domain | Carries image data |
| `StorageGateway` | Infrastructure | Stores file (local filesystem) |
| `ImageRepository` | Data | Persists image record |
| `PostgreSQL` | Database | Executes all queries |

---

## The Conversation

---

**Karim:** I want to add my new hotel "Riverside Boutique Bangkok"
to miniAgoda. It is in Bangkok, on Charoen Krung Road, with a pool
and WiFi. Let me start.
*(navigates to Hotel Owner Dashboard → Add New Hotel)*

---

## Part 1: Create the Hotel

---

**Browser:** Building HTTP POST for hotel creation. Karim is
logged in as HOTEL_ADMIN:

```
POST /api/v1/hotels
HTTP/1.1
Host: api.miniagoda.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...karim_jwt
Content-Type: application/json

{
  "name":        "Riverside Boutique Bangkok",
  "description": "A charming boutique hotel on the banks of the Chao Phraya River, offering intimate luxury in the heart of Bangkok's historic district.",
  "address": {
    "street":  "1234 Charoen Krung Rd",
    "area":    "Bang Rak",
    "zipCode": "10500",
    "cityId":  "550e8400-e29b-41d4-a716-446655440000"
  },
  "phoneNumber": {
    "countryCode": "+66",
    "number":      "923456789"
  },
  "amenities": ["WIFI", "POOL", "RESTAURANT"],
  "ownerId":   "karim-uuid-here"
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
  Source IP:        10.20.30.40    (Karim's device)
  Destination IP:   104.21.45.23   (Cloudflare Bangkok)
  Source Port:      60123
  Destination Port: 443
```

Routing to Cloudflare Bangkok.

---

**Internet:** ~3ms to Cloudflare Bangkok PoP.

---

**CDN (Cloudflare):**

**TLS Termination:**
```
POST /api/v1/hotels
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...karim_jwt
Body: { "name": "Riverside Boutique Bangkok", ... }
```

**Cache check:** POST — never cached.

**Bot protection:** Normal request rate. PASS ✅

Forwarding to API Gateway.

---

**API Gateway:**

**Rate limiting:**
```
Key:    rate_limit:user:karim-uuid:add-hotel
Count:  1 attempt
Limit:  10 hotel operations per hour
Status: PASS ✅
```

**JWT expiry check:**
```
Decoding: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...karim_jwt
Payload:  {
            "sub":  "karim-uuid-here",
            "role": "HOTEL_ADMIN",
            "exp":  1703778923
          }
Expiry: valid ✅
```

**Routing:** POST /api/v1/hotels → miniagoda-app:8080

Forwarding with X-Request-ID: req-1a2b3c4d-5e6f-7g8h-9i0j

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
URI:          /api/v1/hotels
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Body:         { "name": "Riverside Boutique Bangkok", ... }
```

Handing to DispatcherServlet.

---

**DispatcherServlet:** Running filter chain.

---

**Spring Security Filter:** JWT present.

**Decode:**
```
Payload: {
  "sub":  "karim-uuid-here",
  "role": "HOTEL_ADMIN",
  "exp":  1703778923
}
Signature: verified ✅
Expiry:    valid ✅
```

**Populate SecurityContext:**
```java
new UsernamePasswordAuthenticationToken(
    "karim-uuid-here",
    null,
    [ROLE_HOTEL_ADMIN]
)
```

No database call. Passing through.

---

**DispatcherServlet:** Routing:
```
POST /api/v1/hotels
→ HotelController.addHotel()
  @PostMapping("/api/v1/hotels")
  @PreAuthorize("hasRole('HOTEL_ADMIN')")  ← HOTEL_ADMIN only
```

Spring Security checks: does SecurityContext have ROLE_HOTEL_ADMIN?
```
[ROLE_HOTEL_ADMIN] ✅ — Karim has the right role
```

---

**HotelController:** Received request.

Deserializing to AddHotelRequest:
```java
AddHotelRequest request = new AddHotelRequest(
    name:        "Riverside Boutique Bangkok",
    description: "A charming boutique hotel...",
    address:     Address("1234 Charoen Krung Rd", "Bang Rak",
                         "10500", UUID("550e8400...")),
    phoneNumber: PhoneNumber("+66", "923456789"),
    amenities:   [WIFI, POOL, RESTAURANT],
    ownerId:     UUID("karim-uuid-here")
);
```

Running @Valid:
```
name:        not blank ✅
address:     not null ✅
phoneNumber: not null, countryCode starts with + ✅
amenities:   valid enum values ✅
ownerId:     present ✅
```

Calling HotelService.addHotel(request).

---

**HotelService:** Received AddHotelRequest.

**Step 1 — Verify owner has HOTEL_ADMIN role:**
UserRepository, does user karim-uuid-here have HOTEL_ADMIN role?

---

**UserRepository:**
```sql
SELECT id, role, status
FROM   users
WHERE  id   = 'karim-uuid-here'
AND    role = 'HOTEL_ADMIN';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(users table)*
```
id:     karim-uuid-here
role:   HOTEL_ADMIN
status: ACTIVE
```

Row found — Karim is HOTEL_ADMIN and ACTIVE. ✅

---

**UserRepository:** Karim verified. Returning to HotelService.

---

**HotelService:**

**Step 2 — Verify cityId exists:**
CityRepository, does city 550e8400... exist?

---

**CityRepository:**
```sql
SELECT id, name
FROM   cities
WHERE  id = '550e8400-e29b-41d4-a716-446655440000';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(cities table)*
```
id:   550e8400-e29b-41d4-a716-446655440000
name: Bangkok
```

City exists ✅

---

**CityRepository:** Bangkok found. Returning to HotelService.

---

**HotelService:**

**Step 3 — Build Hotel with defaults:**
```java
Hotel hotel = new Hotel(
    id:             UUID.randomUUID(),
                    // "l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a"
    name:           "Riverside Boutique Bangkok",
    address:        Address("1234 Charoen Krung Rd", "Bang Rak",
                             "10500", UUID("550e8400...")),
    rating:         0.0,              // default — no reviews yet
    status:         HotelStatus.PENDING,  // default — awaiting activation
    description:    "A charming boutique hotel...",
    phoneNumber:    PhoneNumber("+66", "923456789"),
    amenities:      [WIFI, POOL, RESTAURANT],
    roomTypes:      [],               // empty — added via RoomTypeService
    primaryImageId: null,             // set after image upload
    ownerId:        UUID("karim-uuid-here")
);
```

Note: Status defaults to PENDING — the hotel is NOT visible in
search results until an admin activates it via
`AdminService.activateHotel()` or `HotelService.activateHotel()`.

**Step 4 — Save hotel:**
HotelRepository, save this hotel.

---

**HotelRepository:** Generating SQL:
```sql
INSERT INTO hotels (
    id, name, description, rating, status,
    phone_country_code, phone_number,
    primary_image_id, owner_id,
    created_at, updated_at
) VALUES (
    'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a',
    'Riverside Boutique Bangkok',
    'A charming boutique hotel...',
    0.0,
    'PENDING',
    '+66', '923456789',
    NULL,
    'karim-uuid-here',
    '2024-12-18T15:00:00Z',
    '2024-12-18T15:00:00Z'
);
```

Also inserting address and amenities:
```sql
INSERT INTO addresses (
    id, street, area, zip_code, city_id, hotel_id
) VALUES (
    UUID.randomUUID(),
    '1234 Charoen Krung Rd', 'Bang Rak', '10500',
    '550e8400-e29b-41d4-a716-446655440000',
    'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a'
);

INSERT INTO hotel_amenities (hotel_id, amenity)
VALUES
    ('l2m3n4o5-...', 'WIFI'),
    ('l2m3n4o5-...', 'POOL'),
    ('l2m3n4o5-...', 'RESTAURANT');
```

PostgreSQL, execute all.

---

**PostgreSQL:**
```
INSERT 0 1  (hotels) ✅
INSERT 0 1  (addresses) ✅
INSERT 0 3  (hotel_amenities) ✅
```

---

**HotelRepository:** All inserts confirmed. Returning saved Hotel to
HotelService.

---

**HotelService:** Hotel created. Returning to HotelController.

---

**HotelController:** Serializing 201 response:

```json
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/hotels/l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a

{
  "hotelId":     "l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a",
  "name":        "Riverside Boutique Bangkok",
  "status":      "PENDING",
  "rating":      0.0,
  "ownerId":     "karim-uuid-here",
  "message":     "Hotel created successfully. Status is PENDING — contact admin to activate."
}
```

---

*(Return path — identical structure. Karim sees success message.)*

---

**Karim:** Hotel created! Now let me add a room type.

---

## Part 2: Add Room Type

---

**Browser:** Building HTTP POST for room type:

```
POST /api/v1/hotels/l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a/room-types
HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...karim_jwt
Content-Type: application/json

{
  "name":       "Deluxe River View",
  "category":   "DELUXE",
  "bedTypes":   ["KING"],
  "totalRooms": 8,
  "capacity":   2
}
```

*(Network layers — TLS → TCP/IP → CDN → API Gateway → LB → Nginx →
Tomcat → DispatcherServlet → Spring Security — identical. JWT verified,
HOTEL_ADMIN role confirmed. Routing to RoomTypeController.)*

---

**RoomTypeController:** Deserializing:
```java
AddRoomTypeRequest request = new AddRoomTypeRequest(
    name:       "Deluxe River View",
    category:   RoomCategory.DELUXE,
    bedTypes:   [BedType.KING],
    totalRooms: 8,
    capacity:   2
);
UUID hotelId = UUID("l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a");
```

Running @Valid:
```
name:       not blank ✅
category:   valid enum ✅
bedTypes:   not empty ✅
totalRooms: 8 ≥ 1 ✅
capacity:   2 ≥ 1 ✅
```

Calling RoomTypeService.addRoomType(hotelId, request).

---

**RoomTypeService:** Received AddRoomTypeRequest.

**Step 1 — Verify hotel exists and belongs to Karim:**
HotelRepository, find hotel l2m3n4o5... and check ownerId.

---

**HotelRepository:**
```sql
SELECT id, owner_id, status
FROM   hotels
WHERE  id = 'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a';
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
id:       l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a
owner_id: karim-uuid-here   ✅
status:   PENDING
```

---

**HotelRepository:** Hotel found — owned by Karim. Returning to
RoomTypeService.

---

**RoomTypeService:**

**Step 2 — Build RoomType:**
```java
RoomType roomType = new RoomType(
    id:             UUID.randomUUID(),
                    // "m3n4o5p6-q7r8-9s0t-1u2v-3w4x5y6z7a8b"
    name:           "Deluxe River View",
    category:       RoomCategory.DELUXE,
    bedTypes:       [BedType.KING],
    totalRooms:     8,
    capacity:       2,
    status:         RoomTypeStatus.ACTIVE,
    primaryImageId: null,
    ratePolicies:   []      // added separately via addRatePolicy()
);
```

**Step 3 — Save room type:**
HotelRepository, add this room type to hotel l2m3n4o5...

---

**HotelRepository:**
```sql
INSERT INTO room_types (
    id, hotel_id, name, category,
    total_rooms, capacity, status,
    primary_image_id, created_at, updated_at
) VALUES (
    'm3n4o5p6-q7r8-9s0t-1u2v-3w4x5y6z7a8b',
    'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a',
    'Deluxe River View',
    'DELUXE',
    8, 2,
    'ACTIVE',
    NULL,
    '2024-12-18T15:01:00Z',
    '2024-12-18T15:01:00Z'
);

INSERT INTO room_type_bed_types (room_type_id, bed_type)
VALUES ('m3n4o5p6-...', 'KING');
```

PostgreSQL, execute.

---

**PostgreSQL:**
```
INSERT 0 1  (room_types) ✅
INSERT 0 1  (room_type_bed_types) ✅
```

---

**HotelRepository:** Room type saved. Returning to RoomTypeService.

---

**RoomTypeService:**

**Step 4 — Initialize inventory in AvailabilityService:**

This is critical. When a room type is created, AvailabilityService
must know it exists and how many rooms it has. Without this, the
room type would never appear in availability checks.

AvailabilityService, initialize inventory for room type
m3n4o5p6... with 8 total rooms.

---

**AvailabilityService:** Received inventory initialization request.

AvailabilityRepository, store inventory record for this room type.

---

**AvailabilityRepository:** Generating SQL:
```sql
INSERT INTO availability (
    room_type_id, hotel_id, total_rooms,
    created_at, updated_at
) VALUES (
    'm3n4o5p6-q7r8-9s0t-1u2v-3w4x5y6z7a8b',
    'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a',
    8,
    '2024-12-18T15:01:01Z',
    '2024-12-18T15:01:01Z'
);
```

PostgreSQL, execute.

---

**PostgreSQL:** *(availability table)*
```
INSERT 0 1 ✅

Room type m3n4o5p6... is now known to AvailabilityService.
Available rooms will be computed as:
  totalRooms(8) - bookedRooms(from bookings table)
```

---

**AvailabilityRepository:** Initialized. Returning to AvailabilityService.

---

**AvailabilityService:** Inventory initialized for 8 rooms.
Returning to RoomTypeService.

---

**RoomTypeService:** Room type created and inventory initialized.
Returning to RoomTypeController.

---

**RoomTypeController:** Serializing 201 response:

```json
HTTP/1.1 201 Created
Location: /api/v1/hotels/l2m3n4o5-.../room-types/m3n4o5p6-...

{
  "roomTypeId":  "m3n4o5p6-q7r8-9s0t-1u2v-3w4x5y6z7a8b",
  "hotelId":     "l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a",
  "name":        "Deluxe River View",
  "category":    "DELUXE",
  "bedTypes":    ["KING"],
  "totalRooms":  8,
  "capacity":    2,
  "status":      "ACTIVE",
  "message":     "Room type created. Inventory initialized for 8 rooms."
}
```

---

**Karim:** Room type added. Now let me upload the primary image.

---

## Part 3: Upload and Confirm Primary Image

---

**Browser:** Building multipart HTTP POST for image upload:

```
POST /api/v1/images/upload
HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...karim_jwt
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="entityId"

l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a
------WebKitFormBoundary
Content-Disposition: form-data; name="entityType"

HOTEL
------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="riverside-hotel.jpg"
Content-Type: image/jpeg

[binary image data — 2.3MB JPEG]
------WebKitFormBoundary--
```

Handing to TLS. The image binary is encrypted along with everything else.

---

*(Network layers — TLS → TCP/IP → CDN → API Gateway → LB → Nginx →
Tomcat → DispatcherServlet → Spring Security — identical. HOTEL_ADMIN
confirmed. Routing to ImageController.)*

---

**ImageController:** Received multipart request.

Parsing multipart form data:
```java
ImageUploadRequest request = new ImageUploadRequest(
    entityId:    UUID("l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a"),
    entityType:  ImageEntityType.HOTEL,
    data:        [binary JPEG bytes — 2.3MB],
    fileName:    "riverside-hotel.jpg",
    contentType: "image/jpeg"
);
```

Calling ImageService.uploadImage(request).

---

**ImageService:** Received ImageUploadRequest.

**Step 1 — Verify entity exists:**
HotelRepository, does hotel l2m3n4o5... exist?

---

**HotelRepository:**
```sql
SELECT id FROM hotels
WHERE id = 'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a';
```

PostgreSQL: Row found ✅

---

**ImageService:**

**Step 2 — Store file via StorageGateway:**
StorageGateway, store this 2.3MB JPEG.

---

**StorageGateway:** *(LocalStorageGateway implementation)*

Generating unique file key:
```
fileKey: hotels/l2m3n4o5-.../riverside-hotel-1703692800.jpg
```

Writing to local filesystem:
```
Path:  /var/miniagoda/storage/hotels/l2m3n4o5-.../
File:  riverside-hotel-1703692800.jpg
Size:  2,411,520 bytes (2.3MB)
```

File written successfully. Generating URL:
```
URL: https://storage.miniagoda.com/hotels/l2m3n4o5-.../riverside-hotel-1703692800.jpg
```

Note: In production this would be an S3 URL. For now it is a
local file server URL. The StorageGateway abstraction means
ImageService does not care which backend is used.

Returning URL to ImageService.

---

**ImageService:** File stored. URL received.

**Step 3 — Create unconfirmed Image record:**
```java
Image image = new Image(
    id:         UUID.randomUUID(),
              // "n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c"
    entityId:   UUID("l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a"),
    entityType: ImageEntityType.HOTEL,
    url:        "https://storage.miniagoda.com/hotels/.../riverside-hotel-1703692800.jpg",
    confirmed:  false,    // not yet confirmed
    status:     ImageStatus.ACTIVE,
    createdAt:  LocalDateTime.now(),
    updatedAt:  LocalDateTime.now()
);
```

ImageRepository, save this image.

---

**ImageRepository:**
```sql
INSERT INTO images (
    id, entity_id, entity_type, url,
    confirmed, status, created_at, updated_at
) VALUES (
    'n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c',
    'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a',
    'HOTEL',
    'https://storage.miniagoda.com/hotels/.../riverside-hotel-1703692800.jpg',
    false,
    'ACTIVE',
    '2024-12-18T15:02:00Z',
    '2024-12-18T15:02:00Z'
);
```

PostgreSQL: INSERT 0 1 ✅

---

**ImageRepository:** Saved. Returning to ImageService.

---

**ImageService:** Image saved as unconfirmed. Returning to
ImageController.

---

**ImageController:** Serializing 201 response:

```json
HTTP/1.1 201 Created

{
  "imageId":   "n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c",
  "entityId":  "l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a",
  "entityType": "HOTEL",
  "url":       "https://storage.miniagoda.com/hotels/.../riverside-hotel-1703692800.jpg",
  "confirmed": false,
  "message":   "Image uploaded. Call /confirm to make it publicly visible."
}
```

---

**Karim:** Image uploaded. Now I need to confirm it.

---

**Browser:** Building HTTP PATCH to confirm the image:

```
PATCH /api/v1/images/n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c/confirm
HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...karim_jwt
```

*(Network layers identical. Routing to ImageController.confirmImage().)*

---

**ImageController:** Calling ImageService.confirmImage(imageId).

---

**ImageService:** Received confirmation request.

**Step 1 — Look up image:**
ImageRepository, find image n4o5p6q7...

---

**ImageRepository:**
```sql
SELECT id, entity_id, entity_type, url, confirmed, status
FROM   images
WHERE  id = 'n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c';
```

PostgreSQL: image found, confirmed=false ✅

---

**ImageService:**

**Step 2 — Confirm image:**
```sql
UPDATE images
SET    confirmed  = true,
       updated_at = '2024-12-18T15:02:30Z'
WHERE  id = 'n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c';
```

PostgreSQL: UPDATE 1 ✅

Image is now confirmed and publicly visible.

---

**Step 3 — Set as hotel's primary image:**

Karim also wants this as the hotel's primary image. Calling
HotelService to update primaryImageId.

---

**HotelRepository:**
```sql
UPDATE hotels
SET    primary_image_id = 'n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c',
       updated_at       = '2024-12-18T15:02:31Z'
WHERE  id = 'l2m3n4o5-p6q7-8r9s-0t1u-2v3w4x5y6z7a';
```

PostgreSQL: UPDATE 1 ✅

---

**ImageController:** Serializing 200 response:

```json
HTTP/1.1 200 OK

{
  "imageId":   "n4o5p6q7-r8s9-0t1u-2v3w-4x5y6z7a8b9c",
  "confirmed": true,
  "url":       "https://storage.miniagoda.com/hotels/.../riverside-hotel-1703692800.jpg",
  "message":   "Image confirmed and set as primary hotel image."
}
```

---

**Karim:** Everything is set up. My hotel is created, room type
added, inventory initialized, and primary image uploaded.

---

**Browser:** Rendering final state:

```
✅ Hotel Setup Complete!

Riverside Boutique Bangkok
Bang Rak, Bangkok

Status:      PENDING (awaiting admin activation)
Room Types:  1 (Deluxe River View — 8 rooms)
Primary Image: ✅ Uploaded

Next Steps:
  1. Add rate policies to your room type
  2. Contact admin to activate your hotel
  3. Hotel will appear in search once ACTIVE

[Add Rate Policy]   [View Hotel]   [Contact Support]
```

---

**Karim:** I need to add rate policies next and then contact
miniAgoda admin to activate the hotel.

---

## What Happens Next

```
1. Karim adds RatePolicy via:
   POST /api/v1/hotels/{hotelId}/room-types/{roomTypeId}/rate-policies

2. Admin activates hotel via:
   PATCH /api/v1/admin/hotels/{hotelId}/activate
   → UPDATE hotels SET status='ACTIVE'

3. Hotel appears in search:
   HotelSearchService.searchByCity() returns it because status=ACTIVE
   AvailabilityService finds 8 rooms available (0 booked so far)
```

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Karim | Fills hotel creation form |
| 2 | Browser | HTTP POST /hotels with HOTEL_ADMIN JWT |
| 3–8 | Network layers | CDN → Gateway → LB → Nginx → Tomcat |
| 9 | Spring Security | JWT verified — HOTEL_ADMIN role ✅ |
| 10 | HotelController | Deserialize → AddHotelRequest |
| 11 | HotelService | Verify Karim has HOTEL_ADMIN role |
| 12 | UserRepository | SELECT user — role=HOTEL_ADMIN ✅ |
| 13 | HotelService | Verify cityId exists |
| 14 | CityRepository | SELECT city — Bangkok found ✅ |
| 15 | HotelService | Build Hotel — status=PENDING, rating=0.0 |
| 16 | HotelRepository | INSERT hotel + address + amenities |
| 17 | PostgreSQL | Hotel rows committed |
| 18 | HotelController | 201 Created — status=PENDING |
| 19 | Karim | Adds room type |
| 20 | RoomTypeController | Deserialize → AddRoomTypeRequest |
| 21 | RoomTypeService | Verify hotel ownership |
| 22 | HotelRepository | INSERT room_type + bed_types |
| 23 | PostgreSQL | Room type rows committed |
| 24 | RoomTypeService | Initialize inventory |
| 25 | AvailabilityService | INSERT availability record |
| 26 | AvailabilityRepository | 8 rooms registered ✅ |
| 27 | PostgreSQL | Availability record committed |
| 28 | RoomTypeController | 201 Created — inventory initialized |
| 29 | Karim | Uploads primary image |
| 30 | ImageController | Parse multipart — ImageUploadRequest |
| 31 | ImageService | Verify hotel exists |
| 32 | StorageGateway | Write 2.3MB JPEG to local filesystem |
| 33 | ImageService | INSERT image — confirmed=false |
| 34 | PostgreSQL | Image row committed |
| 35 | ImageController | 201 Created — needs confirmation |
| 36 | Karim | Confirms image |
| 37 | ImageService | UPDATE confirmed=true |
| 38 | HotelRepository | UPDATE primary_image_id |
| 39 | PostgreSQL | Both updates committed |
| 40 | Browser | "Hotel Setup Complete" — awaiting activation |