# API Contract: Admin

## Overview

`AdminService` is responsible for system-wide user management, content
moderation, revenue reporting across scopes, and system statistics. It is
NOT responsible for hotel data management (that's `HotelService`), booking
operations (that's `BookingService`), or regular user profile management
(that's `UserService`).

All `AdminService` methods require `SUPER_ADMIN` role, enforced via
Spring Security `@PreAuthorize("hasRole('SUPER_ADMIN')")`. See
[ADR-008](../architecture/decisions/ADR-008-spring-security-authorization.md).

## Collaborators

```java
@Service
public class AdminService {
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final BookingRepository bookingRepository;
    private final ReviewService reviewService;
    private final ImageService imageService;
    private final NotificationService notificationService;
    private final DestinationService destinationService;
}
```

---

## User Management

### `changeRole(UUID userId, UserRole newRole)`

Changes a user's role. Uses `UserRole` enum directly — no roleId UUID.

```java
void changeRole(UUID userId, UserRole newRole);
```

**Behavior:**
1. Look up user — throw `ResourceNotFoundException` if not found
2. Update `role` to `newRole`, set `updatedAt`
3. Revoke all existing tokens — user must log in again to get new role claims

---

### `banUser(UUID userId)`

Bans a user. Banned users cannot log in, book, or write reviews.

```java
void banUser(UUID userId);
```

**Behavior:**
1. Look up user — throw `ResourceNotFoundException` if not found
2. Check status is not already `BANNED` — throw `InvalidUserStateException` if so
3. Set status to `BANNED`, set `updatedAt`
4. Revoke all existing tokens

---

### `unbanUser(UUID userId)`

Reinstates a banned user. Sets status back to `ACTIVE`.

```java
void unbanUser(UUID userId);
```

---

### `getUserById(UUID id)`

Returns a single user by ID. Admin can view any user.

```java
User getUserById(UUID id);
```

---

### `getUserByEmail(String email)`

Returns a single user by email address.

```java
User getUserByEmail(String email);
```

---

### `getAllUsersByStatus(UserStatus status, int page, int size)`

Returns all users filtered by status, paginated.

```java
List<UserSummary> getAllUsersByStatus(UserStatus status, int page, int size);
```

---

## Hotel Management

### `getAllHotelsByStatus(HotelStatus status, int page, int size)`

Returns all hotels filtered by status, paginated.

```java
List<HotelSummary> getAllHotelsByStatus(HotelStatus status, int page, int size);
```

---

## Revenue

### `getRevenue(RevenueScope scope, RevenuePeriod period)`

Returns revenue for the given scope and period.

```java
Revenue getRevenue(RevenueScope scope, RevenuePeriod period);
```

**Scope behavior:**
- `SYSTEM` — aggregates revenue across all hotels
- `HOTEL` — revenue for a specific hotel (delegates to `HotelManagementService`)
- `CITY` — aggregates revenue across all hotels in a city

---

## System Statistics

### `getSystemStats()`

Returns a real-time snapshot of key system metrics.

```java
SystemStats getSystemStats();
```

**Returns:**
- Total users, hotels, bookings, reviews
- Active promotions count
- Users broken down by `UserStatus`
- Bookings broken down by `BookingStatus`
- `asOf` timestamp

---

## Content Moderation

### `moderateContent(UUID contentId, ContentType type, ModerationAction action)`

Unified content moderation entry point. Delegates to the appropriate
service based on `ContentType`.

```java
void moderateContent(UUID contentId, ContentType type, ModerationAction action);
```

**Delegation:**
- `REVIEW` → `ReviewService.activateReview()` / `deactivateReview()`
- `IMAGE` → `ImageService.activateImage()` / `deactivateImage()`
- `NOTIFICATION` → `NotificationService.activateNotification()` / `deactivateNotification()`
- `DESTINATION` → `DestinationService.activateDestination()` / `deactivateDestination()`

---

## Error Cases

| Condition | Exception |
|---|---|
| User not found | `ResourceNotFoundException` |
| Hotel not found | `ResourceNotFoundException` |
| Content not found | `ResourceNotFoundException` |
| User already `BANNED` | `InvalidUserStateException` |
| User already `ACTIVE` on unban | `InvalidUserStateException` |
| Invalid `RevenueScope.scopeId` | `ResourceNotFoundException` |