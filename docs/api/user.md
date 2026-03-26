# API Contract: User

## Overview

`UserService` is responsible for user registration, profile management,
and account deletion. It is NOT responsible for authentication and token
management (that's `AuthService`), role-based access control (that's
`AuthService`), admin user management (that's `AdminService`), or search
history (that's `SearchHistoryService`).

## Collaborators

```java
@Service
public class UserService {
    private final UserRepository userRepository;
}
```

## Registration Flow

```
1. UserService.registerUser()
   → creates User with status INACTIVE
   → returns created User

2. AuthService (triggered after registration)
   → generates email verification token
   → calls NotificationService.sendVerificationEmail() async

3. User clicks email link
   → AuthService.verifyEmail(token)
   → sets User status to ACTIVE
```

---

## Methods

### `registerUser(RegisterRequest request)`

Creates a new user account. Status defaults to `INACTIVE` —
activated after email verification via `AuthService`.

```java
User registerUser(RegisterRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Check email uniqueness — throw `DuplicateEmailException` if exists
3. Hash password
4. Create `User` with status `INACTIVE`, role `GUEST`
5. Set `createdAt` and `updatedAt`
6. Persist and return `User`
7. `AuthService` handles verification token and email (separate concern)

---

### `editUser(UUID id, EditUserRequest request)`

Partially updates a user's profile. `email` and `password` not editable
via this method — handled by `AuthService`.

```java
User editUser(UUID id, EditUserRequest request);
```

**Behavior:**
1. Validate request — at least one field must be present
2. Look up user — throw `ResourceNotFoundException` if not found
3. Apply present fields, set `updatedAt`
4. Persist and return updated `User`

---

### `deactivateUser(UUID id)`

Sets user status to `INACTIVE`. User cannot log in or make bookings.

```java
void deactivateUser(UUID id);
```

---

### `deleteAccount(UUID id)`

Soft deletes a user account. Anonymizes all personal data while
preserving referential integrity for bookings, reviews, and history.

```java
void deleteAccount(UUID id);
```

**Anonymization:**
- `firstName` → `"Deleted"`
- `lastName` → `"User"`
- `email` → `"deleted_{id}@deleted.com"`
- `phoneNumber` → `null`
- `profileImageUrl` → `null`
- `status` → `INACTIVE`
- `updatedAt` → now

---

### `getUserById(UUID id)`

Returns a single user by ID.

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

## Error Cases

| Condition | Exception |
|---|---|
| User not found | `ResourceNotFoundException` |
| Email already exists | `DuplicateEmailException` |
| `EditUserRequest` with no fields | `InvalidUserRequestException` |
| Deactivating already `INACTIVE` user | `InvalidUserStateException` |