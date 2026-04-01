# miniAgoda — HTTP API Reference

> **Base URL:** `https://api.miniagoda.com/v1`  
> **Auth:** Bearer token (JWT) via `Authorization: Bearer <token>` header, except where marked Public.

---

## Table of Contents

- [Guest-Facing](#guest-facing)
  - [HotelController](#hotelcontroller)
  - [SearchController](#searchcontroller)
  - [BookingController](#bookingcontroller)
  - [ReviewController](#reviewcontroller)
  - [RecommendationController](#recommendationcontroller)
- [Identity & Account](#identity--account)
  - [AuthController](#authcontroller)
  - [UserController](#usercontroller)
- [Property Management (Host-Facing)](#property-management-host-facing)
  - [HotelManagementController](#hotelmanagementcontroller)
- [Back-Office (Admin)](#back-office-admin)
  - [AdminController](#admincontroller)

---

## Guest-Facing

### HotelController

**Services:** `HotelService`, `RoomTypeService`, `ImageService`, `DestinationService`

Browse hotels, room types, and destinations.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/hotels` | Public | List hotels with optional filters |
| `GET` | `/hotels/:id` | Public | Hotel detail, room types, and images |
| `GET` | `/hotels/:id/rooms` | Public | All room types for a hotel |
| `GET` | `/destinations` | Public | Browse destinations (autocomplete) |
| `GET` | `/destinations/:id/hotels` | Public | Hotels in a destination |

#### `GET /hotels`

Query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `destination` | `string` | Filter by destination ID or name |
| `stars` | `integer` | Minimum star rating (1–5) |
| `amenities` | `string[]` | Comma-separated amenity keys |
| `page` | `integer` | Page number (default: 1) |
| `limit` | `integer` | Results per page (default: 20, max: 100) |

```
GET /hotels?destination=bangkok&stars=4&amenities=pool,wifi&page=1&limit=20
```

#### `GET /hotels/:id`

Returns hotel detail including embedded room types and image URLs.

```
GET /hotels/ht_abc123
```

#### `GET /destinations`

Query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `q` | `string` | Autocomplete query string |
| `limit` | `integer` | Max results (default: 10) |

```
GET /destinations?q=bang&limit=10
```

---

### SearchController

**Services:** `HotelSearchService`, `AvailabilityService`, `SearchHistoryService`

Full-text availability search and history.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/search` | Public | Search hotels by dates, guests, filters |
| `GET` | `/search/availability` | Public | Check room availability for given dates |
| `GET` | `/search/history` | Required | Authenticated user's recent searches |
| `DELETE` | `/search/history/:id` | Required | Remove a search history entry |

#### `POST /search`

Request body:

```json
{
  "destination": "Bangkok",
  "check_in": "2025-11-01",
  "check_out": "2025-11-05",
  "guests": {
    "adults": 2,
    "children": 1
  },
  "filters": {
    "min_price": 1000,
    "max_price": 5000,
    "stars": 4,
    "amenities": ["pool", "breakfast"]
  },
  "sort": "price_asc",
  "page": 1,
  "limit": 20
}
```

#### `GET /search/availability`

Query parameters:

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `hotel_id` | `string` | Yes | Hotel identifier |
| `room_type_id` | `string` | Yes | Room type identifier |
| `check_in` | `date` | Yes | ISO 8601 date (YYYY-MM-DD) |
| `check_out` | `date` | Yes | ISO 8601 date (YYYY-MM-DD) |
| `guests` | `integer` | Yes | Number of guests |

```
GET /search/availability?hotel_id=ht_abc123&room_type_id=rt_001&check_in=2025-11-01&check_out=2025-11-05&guests=2
```

---

### BookingController

**Services:** `BookingService`, `AvailabilityService`, `PaymentService`, `NotificationService`

Create and manage bookings. All endpoints require authentication.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/bookings` | Required | Create booking and initiate payment |
| `GET` | `/bookings` | Required | User's booking history |
| `GET` | `/bookings/:id` | Required | Booking detail and payment status |
| `PATCH` | `/bookings/:id/cancel` | Required | Cancel booking and trigger refund |
| `POST` | `/bookings/:id/payment` | Required | Retry or confirm payment |

#### `POST /bookings`

Request body:

```json
{
  "hotel_id": "ht_abc123",
  "room_type_id": "rt_001",
  "check_in": "2025-11-01",
  "check_out": "2025-11-05",
  "guests": {
    "adults": 2,
    "children": 1
  },
  "guest_details": {
    "first_name": "Jane",
    "last_name": "Doe",
    "email": "jane@example.com",
    "phone": "+66812345678"
  },
  "promo_code": "SAVE20",
  "payment": {
    "method": "card",
    "token": "tok_visa_xxx"
  }
}
```

Response `201 Created`:

```json
{
  "booking_id": "bk_xyz789",
  "status": "confirmed",
  "total_amount": 4800,
  "currency": "THB",
  "payment_status": "paid"
}
```

#### `PATCH /bookings/:id/cancel`

Request body:

```json
{
  "reason": "Change of plans"
}
```

Response follows the hotel's cancellation policy. Refund eligibility is included in the response.

#### `POST /bookings/:id/payment`

Used to retry a failed payment or confirm a payment that required 3D Secure.

```json
{
  "payment_token": "tok_visa_xxx"
}
```

---

### ReviewController

**Services:** `ReviewService`

Guests may only submit a review after completing a booking at the property.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/hotels/:id/reviews` | Public | Paginated reviews for a hotel |
| `POST` | `/hotels/:id/reviews` | Required | Submit a review |
| `PUT` | `/reviews/:id` | Required | Edit own review |
| `DELETE` | `/reviews/:id` | Required | Delete own review |

#### `GET /hotels/:id/reviews`

Query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `sort` | `string` | `newest` (default), `highest`, `lowest` |
| `page` | `integer` | Page number |
| `limit` | `integer` | Results per page (default: 10) |

#### `POST /hotels/:id/reviews`

Request body:

```json
{
  "booking_id": "bk_xyz789",
  "rating": 4,
  "title": "Great stay, friendly staff",
  "body": "Room was clean and staff were very helpful throughout.",
  "categories": {
    "cleanliness": 5,
    "location": 4,
    "value": 4,
    "service": 5
  }
}
```

---

### RecommendationController

**Services:** `RecommendationService`, `PromotionService`

Personalised picks, trending content, and promotions.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/recommendations` | Required | Personalised hotel picks for the user |
| `GET` | `/recommendations/trending` | Public | Trending destinations and hotels |
| `GET` | `/promotions` | Public | Active deals and discount banners |
| `GET` | `/promotions/:code` | Public | Validate a promo code |

#### `GET /recommendations`

Query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `limit` | `integer` | Number of recommendations (default: 10) |
| `destination` | `string` | Bias results toward a destination |

#### `GET /promotions/:code`

Returns validity, discount type, amount, and expiry date for the given promo code.

```
GET /promotions/SAVE20
```

---

## Identity & Account

### AuthController

**Services:** `AuthService`, `UserService`, `NotificationService`

All auth endpoints are public (unauthenticated).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/auth/register` | Public | Sign up and send verification email |
| `POST` | `/auth/login` | Public | Login — returns JWT access and refresh tokens |
| `POST` | `/auth/logout` | Required | Invalidate refresh token |
| `POST` | `/auth/refresh` | Public | Rotate access token using refresh token |
| `POST` | `/auth/forgot-password` | Public | Send password reset email |
| `POST` | `/auth/reset-password` | Public | Apply new password using reset token |

#### `POST /auth/register`

```json
{
  "email": "jane@example.com",
  "password": "StrongPass123!",
  "first_name": "Jane",
  "last_name": "Doe"
}
```

Response `201 Created`:

```json
{
  "user_id": "usr_001",
  "email": "jane@example.com",
  "message": "Verification email sent"
}
```

#### `POST /auth/login`

```json
{
  "email": "jane@example.com",
  "password": "StrongPass123!"
}
```

Response `200 OK`:

```json
{
  "access_token": "eyJ...",
  "refresh_token": "dGh...",
  "expires_in": 3600
}
```

#### `POST /auth/refresh`

```json
{
  "refresh_token": "dGh..."
}
```

#### `POST /auth/reset-password`

```json
{
  "token": "reset_token_from_email",
  "new_password": "NewStrongPass456!"
}
```

---

### UserController

**Services:** `UserService`, `NotificationService`

All endpoints require authentication. Users may only access their own data.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/users/me` | Required | Get own profile |
| `PATCH` | `/users/me` | Required | Update name, phone, or preferences |
| `GET` | `/users/me/notifications` | Required | Notification inbox |
| `PATCH` | `/users/me/notifications/:id` | Required | Mark notification as read |

#### `PATCH /users/me`

All fields are optional — include only those to update.

```json
{
  "first_name": "Jane",
  "phone": "+66812345678",
  "preferences": {
    "currency": "THB",
    "language": "en",
    "notifications": {
      "email": true,
      "push": false
    }
  }
}
```

#### `GET /users/me/notifications`

Query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `unread` | `boolean` | Filter to unread only |
| `page` | `integer` | Page number |

---

## Property Management (Host-Facing)

### HotelManagementController

**Services:** `HotelManagementService`, `AvailabilityService`, `ImageService`, `RoomTypeService`

Host-only endpoints. Requires a JWT with the `host` role. All routes are under `/manage/`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/manage/hotels` | Host | Register a new property |
| `PUT` | `/manage/hotels/:id` | Host | Update hotel info and policies |
| `POST` | `/manage/hotels/:id/rooms` | Host | Add a room type |
| `PATCH` | `/manage/hotels/:id/availability` | Host | Update room availability and pricing |
| `POST` | `/manage/hotels/:id/images` | Host | Upload property images |
| `DELETE` | `/manage/images/:id` | Host | Remove an image |

#### `POST /manage/hotels`

```json
{
  "name": "The Grand Bangkok",
  "destination_id": "dest_bkk",
  "address": {
    "street": "123 Sukhumvit Rd",
    "city": "Bangkok",
    "country": "TH",
    "postal_code": "10110"
  },
  "stars": 4,
  "description": "A luxury property in the heart of Bangkok.",
  "amenities": ["pool", "gym", "breakfast", "wifi"],
  "policies": {
    "check_in_time": "14:00",
    "check_out_time": "12:00",
    "cancellation": "free_24h"
  }
}
```

#### `POST /manage/hotels/:id/rooms`

```json
{
  "name": "Deluxe King Room",
  "description": "Spacious room with city view.",
  "max_guests": 2,
  "base_price": 2500,
  "currency": "THB",
  "amenities": ["king_bed", "bathtub", "city_view"],
  "total_inventory": 20
}
```

#### `PATCH /manage/hotels/:id/availability`

```json
{
  "room_type_id": "rt_001",
  "date_range": {
    "from": "2025-11-01",
    "to": "2025-11-30"
  },
  "available_rooms": 15,
  "price_override": 2800
}
```

#### `POST /manage/hotels/:id/images`

`Content-Type: multipart/form-data`

| Field | Type | Description |
|-------|------|-------------|
| `file` | `File` | Image file (JPEG or PNG, max 10 MB) |
| `category` | `string` | `exterior`, `room`, `amenity`, `lobby` |
| `caption` | `string` | Optional caption |

---

## Back-Office (Admin)

### AdminController

**Services:** `AdminService`, `UserService`, `HotelService`, `PromotionService`, `ReviewService`

Admin-only endpoints. Requires a JWT with the `admin` role. All routes are under `/admin/`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/admin/users` | Admin | List all users with filters |
| `PATCH` | `/admin/users/:id/status` | Admin | Suspend or activate a user |
| `GET` | `/admin/hotels` | Admin | All properties, including pending approval |
| `PATCH` | `/admin/hotels/:id/approve` | Admin | Approve or reject a listing |
| `POST` | `/admin/promotions` | Admin | Create a platform-wide promotion |
| `DELETE` | `/admin/reviews/:id` | Admin | Moderate or remove a review |

#### `GET /admin/users`

Query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `status` | `string` | `active`, `suspended`, `unverified` |
| `role` | `string` | `guest`, `host`, `admin` |
| `q` | `string` | Search by name or email |
| `page` | `integer` | Page number |
| `limit` | `integer` | Results per page |

#### `PATCH /admin/users/:id/status`

```json
{
  "status": "suspended",
  "reason": "Violation of terms of service"
}
```

#### `PATCH /admin/hotels/:id/approve`

```json
{
  "decision": "approved",
  "note": "All documents verified."
}
```

`decision` can be `approved` or `rejected`. A notification is sent to the host automatically.

#### `POST /admin/promotions`

```json
{
  "code": "SAVE20",
  "description": "20% off all bookings in November",
  "discount_type": "percentage",
  "discount_value": 20,
  "valid_from": "2025-11-01",
  "valid_until": "2025-11-30",
  "min_booking_amount": 2000,
  "max_uses": 5000,
  "applicable_to": "all"
}
```

---

## Common Response Shapes

### Success

```json
{
  "data": { },
  "meta": {
    "page": 1,
    "limit": 20,
    "total": 142
  }
}
```

### Error

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "check_out must be after check_in",
    "field": "check_out"
  }
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| `200` | OK |
| `201` | Created |
| `204` | No Content (DELETE success) |
| `400` | Bad Request — validation failed |
| `401` | Unauthorized — missing or invalid token |
| `403` | Forbidden — insufficient role |
| `404` | Not Found |
| `409` | Conflict — e.g. room no longer available |
| `422` | Unprocessable Entity — business rule violation |
| `429` | Too Many Requests — rate limit exceeded |
| `500` | Internal Server Error |