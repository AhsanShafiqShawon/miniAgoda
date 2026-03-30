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
| Full results | Logged in (Shawon) | 2 hotels found, discount applied | [search-logged-in-full-results.md](search/search-logged-in-full-results.md) |
| Insufficient results | Anonymous | Below threshold → recommendations triggered | [search-anonymous-insufficient-results.md](search/search-anonymous-insufficient-results.md) |
| No results | Logged in (Shawon) | Zero results → relaxed dates → relaxed guests | [search-logged-in-no-results.md](search/search-logged-in-no-results.md) |

## Registration Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful registration | Account created INACTIVE, verification email sent | [registration-success.md](registration/registration-success.md) |
| Duplicate email | 409 Conflict — no user created, no email sent | [registration-duplicate-email.md](registration/registration-duplicate-email.md) |
| Email verification | Token validated, account activated ACTIVE | [registration-email-verification.md](registration/registration-email-verification.md) |

## Login Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful login | JWT pair issued, immediately used for search | [login-success.md](login/login-success.md) |
| Wrong password | 401 Unauthorized — BCrypt runs but fails | [login-wrong-password.md](login/login-wrong-password.md) |
| Unverified account | 403 Forbidden — status=INACTIVE | [login-unverified-account.md](login/login-unverified-account.md) |
| Banned account | 403 Forbidden — status=BANNED | [login-banned-account.md](login/login-banned-account.md) |

## Booking Flows

| Scenario | Outcome | File |
|---|---|---|
| Successful booking | Room blocked, inventory updated, confirmation sent | [booking-success.md](booking/booking-success.md) |
| Room unavailable (TOCTOU) | 409 Conflict — room taken between search and booking | [booking-room-unavailable.md](booking/booking-room-unavailable.md) |
| Booking with promotion | Promo validated, usage incremented, reduced price | [booking-with-promotion.md](booking/booking-with-promotion.md) |

## Coming Soon

| Flow | Description |
|---|---|
| Cancel Booking | Booking cancelled, inventory released, refund triggered |
| Write Review | Review submitted, hotel rating recalculated |
| Add Hotel | Hotel owner adds property, inventory initialized |