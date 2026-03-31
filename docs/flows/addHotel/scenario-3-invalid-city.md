# Scenario 3: Add Hotel — Invalid City

**User:** Karim (HOTEL_ADMIN)
**Action:** Tries to add a hotel with a non-existent cityId
**Outcome:** 404 Not Found — city does not exist

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 3 |
|---|---|---|
| cityId | Valid Bangkok UUID | Non-existent UUID |
| City check | Found ✅ | Not found ❌ |
| Hotel created | Yes | No |
| HTTP Status | 201 Created | 404 Not Found |
| Response time | ~312ms | ~85ms |

---

## The Conversation

*(Network layers and Spring Security — identical to Scenario 1.
HOTEL_ADMIN role confirmed. Picking up at HotelService.)*

---

**HotelService:** Received AddHotelRequest.

**Step 1 — Verify owner has HOTEL_ADMIN role:**
UserRepository, check Karim's role.

---

**UserRepository:**
```sql
SELECT id, role, status FROM users
WHERE id = 'karim-uuid-here' AND role = 'HOTEL_ADMIN';
```

PostgreSQL: Karim found — HOTEL_ADMIN, ACTIVE ✅

---

**HotelService:**

**Step 2 — Verify cityId exists:**
CityRepository, does city
`99999999-9999-9999-9999-999999999999` exist?

---

**CityRepository:**
```sql
SELECT id, name
FROM   cities
WHERE  id = '99999999-9999-9999-9999-999999999999';
```

PostgreSQL, execute.

---

**PostgreSQL:** *(cities table)*
```
0 rows returned.

No city with this ID exists.
```

---

**CityRepository:** City not found. Returning null to HotelService.

---

**HotelService:** City does not exist.

Throwing ResourceNotFoundException:
```java
throw new ResourceNotFoundException(
    "City with ID 99999999-9999-9999-9999-999999999999 not found. " +
    "Please provide a valid cityId."
);
```

No hotel created. No amenities inserted. No address inserted.

---

**GlobalExceptionHandler:**
```java
@ExceptionHandler(ResourceNotFoundException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public ErrorResponse handleResourceNotFound(ResourceNotFoundException ex) {
    return new ErrorResponse(
        status:  404,
        error:   "Not Found",
        message: ex.getMessage(),
        path:    "/api/v1/hotels"
    );
}
```

---

**HotelController:** Serializing 404:

```json
HTTP/1.1 404 Not Found
Content-Type: application/json
X-Request-ID: req-3c4d5e6f-7g8h-9i0j-1k2l

{
  "status":  404,
  "error":   "Not Found",
  "message": "City with ID 99999999-9999-9999-9999-999999999999 not found. Please provide a valid cityId.",
  "path":    "/api/v1/hotels"
}
```

---

**Browser:** Received 404. Rendering:

```
⚠️ City Not Found

The selected city could not be found.
Please select a valid city from the dropdown.

[Go Back]   [Search Cities]
```

---

**Karim:** I must have used a wrong city ID. Let me select Bangkok
from the city dropdown instead.

---

## Why 404 Not 400?

```
400 Bad Request — the request itself is malformed
  (e.g. missing required field, wrong data type)

404 Not Found — the request is well-formed but the
  referenced resource does not exist

In this case:
  - cityId format is valid (UUID) → not a 400
  - cityId references non-existent city → 404
  - Same applies to non-existent hotelId, userId, etc.
```

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Karim | Submits hotel with invalid cityId |
| 2–10 | Network + Spring | Identical to Scenario 1 |
| 11 | HotelService | Verify HOTEL_ADMIN role ✅ |
| 12 | UserRepository | Karim found — HOTEL_ADMIN ✅ |
| 13 | HotelService | Verify cityId exists |
| 14 | CityRepository | SELECT city — 0 rows |
| 15 | PostgreSQL | City not found |
| 16 | HotelService | Throw ResourceNotFoundException |
| 17 | GlobalExceptionHandler | 404 Not Found |
| 18 | Return path | 404 in ~85ms — no hotel created |
| 19 | Browser | "Select a valid city" message |