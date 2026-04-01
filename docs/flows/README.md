# Flow Conversations

End-to-end scenarios narrated as conversations between every layer and
class involved — from the user's browser all the way to the database
and back. Each character speaks in first person, passing the baton to
the next.

These flows exist to build a deep, felt understanding of how the system
works before writing a single line of code.

---

## Search Flows

| Scenario | User | Outcome | File |
|---|---|---|---|
| Full results | Logged in (Shawon) | 2 hotels found, discount applied | [search-logged-in-full-results.md](search/scenario-1-search-logged-in-full-results.md) |
| Insufficient results | Anonymous | Below threshold → recommendations triggered | [search-anonymous-insufficient-results.md](search/scenario-2-anonymous-insufficient-results.md) |
| No results | Logged in (Shawon) | Zero results → relaxed dates → relaxed guests | [search-logged-in-no-results.md](search/scenario-3-logged-in-no-results.md) |

## Registration Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful registration | Account created INACTIVE, verification email sent | [registration-success.md](registration/scenario-1-registration-success.md) |
| Duplicate email | 409 Conflict — no user created, no email sent | [registration-duplicate-email.md](registration/scenario-2-registration-duplicate-email.md) |
| Email verification | Token validated, account activated ACTIVE | [registration-email-verification.md](registration/scenario-3-registration-email-verification.md) |

## Login Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful login | JWT pair issued, immediately used for search | [login-success.md](login/scenario-1-successful-login.md) |
| Wrong password | 401 Unauthorized — BCrypt runs but fails | [login-wrong-password.md](login/scenario-2-wrong-password.md) |
| Unverified account | 403 Forbidden — status=INACTIVE | [login-unverified-account.md](login/scenario-3-unverified-account.md) |
| Banned account | 403 Forbidden — status=BANNED | [login-banned-account.md](login/scenario-4-banned-account.md) |

## Booking Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful booking | Room blocked, inventory updated, confirmation sent | [booking-success.md](booking/scenario-1-successful-booking.md) |
| Room unavailable (TOCTOU) | 409 Conflict — room taken between search and booking | [booking-room-unavailable.md](booking/scenario-2-room-unavailable.md) |
| Booking with promotion | Promo validated, usage incremented, reduced price | [booking-with-promotion.md](booking/scenario-3-booking-with-promotion.md) |

## Cancel Booking Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful cancellation | CANCELLED, inventory released, Stripe refund, email sent | [cancel-booking-success.md](cancelBooking/scenario-1-successful-cancellation.md) |
| Already cancelled | 409 Conflict — double cancellation rejected | [cancel-booking-already-cancelled.md](cancelBooking/scenario-2-already-cancelled.md) |
| Completed stay | 409 Conflict — cannot cancel finished stay | [cancel-booking-completed.md](cancelBooking/scenario-3-completed-stay.md) |

## Review Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful review | Review saved, hotel rating recalculated, email sent | [review-success.md](review/scenario-1-successful-review.md) |
| Booking not completed | 409 Conflict — cannot review future or active booking | [review-booking-not-completed.md](review/scenario-2-booking-not-completed.md) |
| Duplicate review | 409 Conflict — one review per booking enforced | [review-duplicate.md](review/scenario-3-duplicate-review.md) |

## Add Hotel Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful hotel addition | Hotel PENDING, room type added, inventory initialized, image uploaded | [add-hotel-success.md](addHotel/scenario-1-successful-hotel-addition.md) |
| Unauthorized role | 403 Forbidden — GUEST cannot add hotels | [add-hotel-unauthorized.md](addHotel/scenario-2-unauthorized-role.md) |
| Invalid city | 404 Not Found — cityId does not exist | [add-hotel-invalid-city.md](addHotel/scenario-3-invalid-city.md) |

## Payment Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful payment | 3DS passed, card charged, booking CONFIRMED, availability blocked, notifications sent | [payment-happy-path.md](payment/scenario-1-payment-happy-path.md) |
| Booking expired | 409 Conflict — hold timed out before payment, Stripe never called | [payment-scenario-1-booking-expired.md](payment/scenario-2-booking-expired.md) |
| Duplicate payment attempt | Idempotency replay — API Gateway + DB constraint, Stripe called exactly once | [payment-scenario-3-idempotency.md](payment/scenario-3-idempotency-replay.md) |
| Room no longer available | 409 Conflict — race condition, final re-check blocks charge, alternatives offered | [payment-scenario-4-race-condition.md](payment/scenario-4-race-condition.md) |
| Stripe webhook delayed | Webhook stalls — browser poll triggers direct Stripe API query, delayed webhook arrives as no-op | [payment-scenario-5-promo-invalid.md](payment/scenario-5-promo-invalid-or-expired.md) |
| Promo code invalid or expired | 422 Unprocessable — validate endpoint rejects before payment, PaymentService re-validates server-side | [payment-scenario-6-gateway-timeout.md](payment/scenario-6-gateway-timeout.md) |
| Payment gateway timeout | 202 Accepted — outcome ambiguous, GATEWAY_TIMEOUT status, reconciliation scheduler resolves within 2 minutes | [payment-scenario-7-webhook-delayed.md](payment/scenario-7-webhook-delayed.md) |