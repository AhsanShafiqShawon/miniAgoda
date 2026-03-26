# API Contract: Promotion

## Overview

`PromotionService` is responsible for managing promotional codes,
validating promotions against bookings, and tracking usage. It is NOT
responsible for `DiscountPolicy` on `RatePolicy` (that's date-range
pricing), payment processing (that's `PaymentService`), or booking
creation (that's `BookingService`).

## Collaborators

```java
@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;
}
```

## Promotion Scopes

| Scope | Who can use it |
|---|---|
| `GLOBAL` | All users |
| `USER` | Specific user only |
| `HOTEL` | Bookings at a specific hotel only |

## Expiry

Promotion expiry is derived from `validTo` on the fly — not stored as
a status. A promotion is considered expired when
`validTo.isBefore(LocalDate.now())`.

---

## Methods

### `createPromotion(CreatePromotionRequest request)`

Creates a new promotion. Admin operation.

```java
Promotion createPromotion(CreatePromotionRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Check `code` uniqueness — throw `DuplicatePromotionCodeException` if exists
3. Create `Promotion` with `usageCount=0`, status `ACTIVE`
4. Persist and return

---

### `createUserPromotion(UUID userId, CreatePromotionRequest request)`

Creates a user-specific promotion. Forces `scope` to `USER` and sets
`userId`. Used for loyalty rewards and first-booking discounts.

```java
Promotion createUserPromotion(UUID userId, CreatePromotionRequest request);
```

---

### `editPromotion(UUID id, EditPromotionRequest request)`

Partially updates a promotion. `code` and `scope` not editable.

```java
Promotion editPromotion(UUID id, EditPromotionRequest request);
```

---

### `activatePromotion(UUID id)`

Sets promotion status to `ACTIVE`.

```java
void activatePromotion(UUID id);
```

---

### `deactivatePromotion(UUID id)`

Sets promotion status to `INACTIVE`.

```java
void deactivatePromotion(UUID id);
```

---

### `getPromotionById(UUID id)`

Returns a single promotion by ID.

```java
Promotion getPromotionById(UUID id);
```

---

### `getPromotionByCode(String code)`

Returns a single promotion by code.

```java
Promotion getPromotionByCode(String code);
```

---

### `getAllPromotions(int page, int size)`

Admin-only. Returns all promotions regardless of status, paginated.

```java
List<Promotion> getAllPromotions(int page, int size);
```

---

### `getAllPromotionsByUser(UUID userId, int page, int size)`

Returns all promotions available to a specific user — includes
`USER` scope promotions for that user and all `GLOBAL` promotions.

```java
List<Promotion> getAllPromotionsByUser(UUID userId, int page, int size);
```

---

### `getAllPromotionsByHotel(UUID hotelId, int page, int size)`

Returns all `HOTEL` scope promotions for a specific hotel, paginated.

```java
List<Promotion> getAllPromotionsByHotel(UUID hotelId, int page, int size);
```

---

### `validatePromotion(UUID promotionId, UUID userId, UUID roomTypeId, BigDecimal bookingPrice)`

Validates a promotion and returns the discount calculation.

```java
ValidatePromotionResult validatePromotion(
    UUID promotionId, UUID userId,
    UUID roomTypeId, BigDecimal bookingPrice);
```

**Behavior:**
1. Look up promotion — throw `ResourceNotFoundException` if not found
2. Check status is `ACTIVE` — throw `InvalidPromotionException` if not
3. Check `validTo` — throw `PromotionExpiredException` if expired
4. Check `validFrom` — throw `PromotionNotYetActiveException` if not yet valid
5. Check `usageCount < usageLimit` — throw `PromotionUsageLimitException` if exceeded
6. Check user usage ≤ `usageLimitPerUser` — throw `PromotionUserLimitException` if exceeded
7. Check scope — if `USER` verify userId matches, if `HOTEL` verify roomType belongs to hotel
8. Calculate `discountAmount` and `finalPrice`
9. Return `ValidatePromotionResult`

---

### `applyPromotion(UUID promotionId, UUID userId)`

Records that a promotion has been used. Increments `usageCount`.
Called by `BookingService` after a booking is confirmed.

```java
void applyPromotion(UUID promotionId, UUID userId);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Promotion not found | `ResourceNotFoundException` |
| Duplicate promotion code | `DuplicatePromotionCodeException` |
| Promotion is `INACTIVE` | `InvalidPromotionException` |
| Promotion has expired | `PromotionExpiredException` |
| Promotion not yet active | `PromotionNotYetActiveException` |
| Total usage limit exceeded | `PromotionUsageLimitException` |
| User usage limit exceeded | `PromotionUserLimitException` |
| Scope mismatch | `InvalidPromotionException` |
| `EditPromotionRequest` with no fields | `InvalidPromotionRequestException` |