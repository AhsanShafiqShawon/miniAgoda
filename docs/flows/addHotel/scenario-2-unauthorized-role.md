# Scenario 2: Add Hotel — Unauthorized Role

**User:** Shawon (logged in as GUEST)
**Action:** Tries to add a hotel
**Outcome:** 403 Forbidden — GUEST role cannot add hotels

---

## Key Differences from Scenario 1

| Aspect | Scenario 1 | Scenario 2 |
|---|---|---|
| User role | HOTEL_ADMIN | GUEST |
| Spring Security check | Passes | Fails at @PreAuthorize |
| Hotel created | Yes | No |
| DB queries | Multiple | Zero |
| HTTP Status | 201 Created | 403 Forbidden |
| Response time | ~312ms | ~15ms |

---

## The Conversation

*(Network layers — identical to Scenario 1. Picking up at
DispatcherServlet after Spring Security Filter.)*

---

**Spring Security Filter:** Decoded Shawon's JWT:
```
Payload: {
  "sub":  "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "role": "GUEST",
  "exp":  1703778923
}
```

Populating SecurityContext with ROLE_GUEST.

---

**DispatcherServlet:** Routing:
```
POST /api/v1/hotels
→ HotelController.addHotel()
  @PostMapping("/api/v1/hotels")
  @PreAuthorize("hasRole('HOTEL_ADMIN')")
```

Checking @PreAuthorize expression:
```
hasRole('HOTEL_ADMIN')
SecurityContext has: [ROLE_GUEST]
ROLE_GUEST ≠ ROLE_HOTEL_ADMIN ❌
```

Spring Security throws AccessDeniedException before
HotelController is ever invoked.

---

**GlobalExceptionHandler:** Caught AccessDeniedException.

```java
@ExceptionHandler(AccessDeniedException.class)
@ResponseStatus(HttpStatus.FORBIDDEN)
public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
    return new ErrorResponse(
        status:  403,
        error:   "Forbidden",
        message: "You do not have permission to perform this action. " +
                 "Hotel management requires HOTEL_ADMIN role.",
        path:    "/api/v1/hotels"
    );
}
```

---

**HotelController is never reached.** No service calls. No database
queries. Spring Security stops the request at the framework level.

---

**Response:**

```json
HTTP/1.1 403 Forbidden
Content-Type: application/json
X-Request-ID: req-2b3c4d5e-6f7g-8h9i-0j1k

{
  "status":  403,
  "error":   "Forbidden",
  "message": "You do not have permission to perform this action. Hotel management requires HOTEL_ADMIN role.",
  "path":    "/api/v1/hotels"
}
```

---

**Browser:** Received 403. Rendering:

```
🚫 Access Denied

You do not have permission to add hotels.
Hotel management requires a Hotel Admin account.

Are you a hotel owner? Contact miniAgoda to upgrade
your account to Hotel Admin.

[Contact Support]   [Back to Home]
```

---

**Shawon:** I need a Hotel Admin account to add hotels.

---

## Why This Is Caught at Spring Level

```
Request flow with GUEST role:

Browser → TLS → TCP/IP → CDN → Gateway → LB → Nginx
→ Tomcat → DispatcherServlet → Spring Security Filter
→ @PreAuthorize("hasRole('HOTEL_ADMIN')") FAILS
→ AccessDeniedException
→ GlobalExceptionHandler
→ 403 Forbidden

HotelController.addHotel() is NEVER called.
HotelService is NEVER called.
No database queries execute.
Response in ~15ms — just JWT decode + role check.
```

This is the power of declarative Spring Security — authorization
is enforced at the framework layer, not scattered in service methods.
See ADR-008.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Tries to add hotel with GUEST role |
| 2–8 | Network layers | Identical to Scenario 1 |
| 9 | Spring Security | JWT decoded — role=GUEST |
| 10 | DispatcherServlet | @PreAuthorize fails — GUEST ≠ HOTEL_ADMIN |
| 11 | Spring Security | AccessDeniedException thrown |
| 12 | GlobalExceptionHandler | 403 Forbidden |
| 13 | Return path | 403 in ~15ms — no DB calls |
| 14 | Browser | "Contact support to upgrade account" |