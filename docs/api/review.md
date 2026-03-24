# API Contract: Review

## Overview

`ReviewService` is responsible for submitting and managing guest reviews,
and keeping hotel ratings up to date. It is NOT responsible for booking
verification (checks that `BookingStatus` is `COMPLETED`), user authentication,
or content moderation beyond activate/deactivate.

## Collaborators

```java
@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
}
```

## Status Lifecycle

```
ACTIVE → INACTIVE   (deactivateReview — admin only)
INACTIVE → ACTIVE   (activateReview — admin only)
```

New reviews are always created with status `ACTIVE`.
Hard deletes are not permitted — admins use `deactivateReview` instead.

---

## Methods

### `writeReview(CreateReviewRequest request)`

Submits a new review for a completed booking.

```java
Review writeReview(CreateReviewRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Look up booking — throw `ResourceNotFoundException` if not found
3. Check `booking.status == COMPLETED` — throw `InvalidReviewRequestException` if not
4. Check no existing review for `bookingId` — throw `DuplicateReviewException` if exists
5. Create `Review` with status `ACTIVE`, set `createdAt`
6. Persist review
7. Recalculate and update hotel rating (internal)
8. Return created `Review`

---

### `editReview(UUID id, EditReviewRequest request)`

Edits an existing review's rating, comment, or both.

```java
Review editReview(UUID id, EditReviewRequest request);
```

**Behavior:**
1. Validate request — at least one field must be present
2. Look up review — throw `ResourceNotFoundException` if not found
3. Check `review.status == ACTIVE` — throw `InvalidReviewStateException` if INACTIVE
4. Apply changes, set `updatedAt`
5. Recalculate and update hotel rating if rating changed (internal)
6. Return updated `Review`

---

### `activateReview(UUID id)`

Restores a previously deactivated review. Admin operation.

```java
void activateReview(UUID id);
```

**Behavior:**
1. Look up review — throw `ResourceNotFoundException` if not found
2. Set status to `ACTIVE`, set `updatedAt`
3. Recalculate and update hotel rating (internal)

---

### `deactivateReview(UUID id)`

Hides a review from public view. Admin operation. Preserves the record.

```java
void deactivateReview(UUID id);
```

**Behavior:**
1. Look up review — throw `ResourceNotFoundException` if not found
2. Set status to `INACTIVE`, set `updatedAt`
3. Recalculate and update hotel rating (internal)

---

### `getReviewById(UUID id)`

Returns a single review by ID.

```java
Review getReviewById(UUID id);
```

---

### `getAllReviewsByHotel(UUID hotelId, int page, int size)`

Returns all `ACTIVE` reviews for a specific hotel, paginated.
Inactive reviews are automatically excluded.

```java
List<Review> getAllReviewsByHotel(UUID hotelId, int page, int size);
```

---

### `getAllReviewsByUser(UUID userId, int page, int size)`

Returns all reviews written by a specific user, paginated.

```java
List<Review> getAllReviewsByUser(UUID userId, int page, int size);
```

---

## Internal Methods

### `calculateRating(UUID hotelId)` — private

Recalculates the hotel's overall rating from all `ACTIVE` reviews.
Called automatically by `writeReview`, `editReview`, `activateReview`,
and `deactivateReview`. Never exposed publicly.

**Calculation:**
1. Fetch all `ACTIVE` reviews for the hotel
2. For each category — average all scores across all reviews
3. Overall rating = average of all five category averages
4. Update `Hotel.rating`

---

## Error Cases

| Condition | Exception |
|---|---|
| Booking or review not found | `ResourceNotFoundException` |
| Booking is not COMPLETED | `InvalidReviewRequestException` |
| Review already exists for bookingId | `DuplicateReviewException` |
| Editing an INACTIVE review | `InvalidReviewStateException` |
| EditReviewRequest with no fields | `InvalidReviewRequestException` |