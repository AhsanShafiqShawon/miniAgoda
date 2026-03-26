# API Contract: Notification

## Overview

`NotificationService` is responsible for sending and managing notifications
for booking events, authentication flows, and review requests. It is NOT
responsible for triggering notifications (callers do that), managing user
preferences (that's `UserService`), or template design.

See [ADR-010](../architecture/decisions/ADR-010-email-service.md) for
the EmailGateway abstraction rationale.

## Collaborators

```java
@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final EmailGateway emailService;
}
```

## Outbox Pattern

```
1. sendNotification(request)
   → Create Notification with status PENDING, readStatus UNREAD
   → Attempt to send via EmailGateway
   → On success: update status to SENT, set sentAt
   → On failure: update status to FAILED (eligible for retry)
```

`FAILED` notifications can be retried by a scheduled job.

## Who Calls NotificationService

All calls are asynchronous — `NotificationService` never blocks the caller:

| Caller | NotificationType |
|---|---|
| `AuthService.verifyEmail()` triggered | `EMAIL_VERIFICATION` |
| `AuthService.requestPasswordReset()` | `PASSWORD_RESET` |
| `BookingService.createBooking()` | `BOOKING_CONFIRMATION` |
| `BookingService.cancelBooking()` | `BOOKING_CANCELLATION` |
| `BookingService.editBooking()` | `BOOKING_EDIT` |
| Scheduled job after checkout | `REVIEW_REQUEST` |

---

## Methods

### `sendNotification(CreateNotificationRequest request)`

Creates a notification record and attempts to send it immediately
unless `scheduledAt` is set.

```java
Notification sendNotification(CreateNotificationRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Create `Notification` with status `PENDING`, `readStatus` `UNREAD`
3. If `scheduledAt` is null — attempt to send immediately via `EmailGateway`
4. If `scheduledAt` is set — defer to scheduled job
5. On success → update status to `SENT`, set `sentAt`
6. On failure → update status to `FAILED`
7. Persist and return

---

### `cancelNotification(UUID id)`

Cancels a pending notification before it is sent.

```java
void cancelNotification(UUID id);
```

**Behavior:**
1. Look up notification — throw `ResourceNotFoundException` if not found
2. Check status is `PENDING` — throw `InvalidNotificationStateException` if not
3. Update status to `CANCELLED`, set `updatedAt`

---

### `markAsRead(UUID id, UUID userId)`

Marks a notification as read. Verifies ownership.

```java
void markAsRead(UUID id, UUID userId);
```

**Behavior:**
1. Look up notification — throw `ResourceNotFoundException` if not found
2. Verify `notification.userId == userId` — throw `UnauthorizedException` if not
3. Update `readStatus` to `READ`, set `updatedAt`

---

### `markAsUnread(UUID id, UUID userId)`

Marks a notification as unread. Verifies ownership.

```java
void markAsUnread(UUID id, UUID userId);
```

---

### `activateNotification(UUID id)`

Sets notification status back to `PENDING` for retry.

```java
void activateNotification(UUID id);
```

---

### `deactivateNotification(UUID id)`

Deactivates a notification — admin operation.

```java
void deactivateNotification(UUID id);
```

---

### `getNotificationById(UUID id)`

Returns a single notification by ID.

```java
Notification getNotificationById(UUID id);
```

---

### `getAllNotificationsByUser(UUID userId, int page, int size)`

Returns all notifications for a user, paginated.

```java
List<Notification> getAllNotificationsByUser(UUID userId, int page, int size);
```

---

### `getAllNotificationsByChannel(Channel channel, int page, int size)`

Returns all notifications for a specific channel, paginated.

```java
List<Notification> getAllNotificationsByChannel(
    Channel channel, int page, int size);
```

---

### `getAllNotificationsByHotel(UUID hotelId, int page, int size)`

Returns all notifications related to a hotel, paginated.
Used for hotel owner notification inbox.

```java
List<Notification> getAllNotificationsByHotel(
    UUID hotelId, int page, int size);
```

---

### `getNotificationsByStatus(NotificationStatus status, int page, int size)`

Returns all notifications filtered by status, paginated.
Admin operation — useful for monitoring FAILED notifications.

```java
List<Notification> getNotificationsByStatus(
    NotificationStatus status, int page, int size);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Notification not found | `ResourceNotFoundException` |
| User not found | `ResourceNotFoundException` |
| Cancelling non-PENDING notification | `InvalidNotificationStateException` |
| User does not own notification | `UnauthorizedException` |
| Email sending failure | Status set to `FAILED` — no exception thrown to caller |