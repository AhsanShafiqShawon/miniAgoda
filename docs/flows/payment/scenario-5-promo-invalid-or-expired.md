# Payment Scenario 5: Promo Code Invalid or Expired

**User:** Shawon (authenticated)
**Booking:** Grand Hyatt Bangkok — Deluxe King, Dec 20–25
**Situation:** Shawon has a promo code `SAVE10` from an old email.
She enters it at checkout. The code exists but expired yesterday.
**Outcome:** Promo rejected cleanly, Shawon told why, given the
option to pay at full price or find another code.

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Validates promo before final payment, then pays |
| `PromoController` | Application | Handles promo validation endpoint |
| `PaymentController` | Application | Handles payment endpoint |
| `PromotionService` | Domain | Validates and applies promo codes |
| `PromotionRepository` | Data | Reads promotion records |
| `PaymentService` | Domain | Orchestrates payment |
| `PostgreSQL` | Database | Stores promotion records |

---

## Background: Two Ways a Promo Fails

This scenario covers **two sub-cases**:

| Sub-case | Code status | Error returned |
|---|---|---|
| A | Code does not exist | `PROMO_NOT_FOUND` |
| B | Code exists but expired | `PROMO_EXPIRED` |
| C | Code exists but wrong user segment | `PROMO_NOT_ELIGIBLE` |

All three are handled by the same PromotionService path. Sub-case B
(expired) is illustrated in full detail below.

---

## The Conversation

---

**Shawon:** On the checkout page. Sees a "Promo code" field. Types
`SAVE10` from an email she received three months ago.
*(clicks "Apply")*

---

## Step 1 — Promo Validation (Before Payment Submission)

The browser validates the promo code as a **separate call** before the
user submits payment. This gives instant feedback without involving
Stripe at all.

---

**Browser:** Building promo validation request:

```
POST /api/v1/promotions/validate
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type:  application/json

{
  "promoCode": "SAVE10",
  "bookingId": "booking-001-uuid",
  "userId":    "shawon-uuid-here"
}
```

---

**PromoController:** Deserialising request. Handing to
PromotionService.validate().

---

**PromotionService:** Received validation request for code `SAVE10`.

PromotionRepository, fetch promotion by code.

---

**PromotionRepository:**
```sql
SELECT promotion_id, code, discount_type, discount_value,
       valid_from, valid_to, usage_limit, usage_count,
       eligible_segment, is_active
FROM   promotions
WHERE  code = 'SAVE10';
```

---

**PostgreSQL:**
```
promotion_id:     promo-042-uuid
code:             SAVE10
discount_type:    PERCENTAGE
discount_value:   10.0
valid_from:       2024-09-01
valid_to:         2024-12-17   ← expired yesterday
usage_limit:      1000
usage_count:      847
eligible_segment: ALL
is_active:        true
```

Returning to PromotionRepository.

---

**PromotionRepository:** Returning Promotion entity to PromotionService.

---

**PromotionService:** Running validation checks:

```
Code exists?          ✅  promo-042-uuid found
Is active?            ✅  is_active = true
valid_from check:     ✅  2024-09-01 ≤ today (2024-12-18)
valid_to check:       ✗   2024-12-17 < today (2024-12-18)  ← EXPIRED
```

Stopping at first failure. Throwing PromoExpiredException:
```
code:      SAVE10
expiredAt: 2024-12-17
```

*(Remaining checks — usage limit, eligibility — are not evaluated.)*

---

**PromotionService:** Returning error to PromoController.

---

**PromoController:** Serialising error response:

```json
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/json

{
  "error":     "PROMO_EXPIRED",
  "code":      "SAVE10",
  "message":   "This promo code expired on 17 Dec 2024.",
  "expiredAt": "2024-12-17"
}
```

---

**Browser:** Received 422. Rendering inline error on checkout page:

```
❌  SAVE10 — This promo code expired on 17 Dec 2024.

Order Summary:
  Grand Hyatt Bangkok · Deluxe King · 5 nights
  Original price:  20,250 THB   (already includes Year-End 10%)
  Promo discount:  —
  Total:           20,250 THB

[ Try Another Code ]   [ Pay 20,250 THB ]
```

---

**Shawon:** Tries another code — `WELCOME5` — that a friend mentioned.
*(clicks "Apply" again)*

---

**Browser:** New validation request:

```
POST /api/v1/promotions/validate

{
  "promoCode": "WELCOME5",
  "bookingId": "booking-001-uuid",
  "userId":    "shawon-uuid-here"
}
```

---

**PromotionRepository:**
```sql
SELECT ... FROM promotions WHERE code = 'WELCOME5';
```

**PostgreSQL:** No rows found.

---

**PromotionService:** Code `WELCOME5` does not exist.
Throwing PromoNotFoundException.

---

**PromoController:**
```json
HTTP/1.1 422 Unprocessable Entity

{
  "error":   "PROMO_NOT_FOUND",
  "code":    "WELCOME5",
  "message": "Promo code WELCOME5 not found."
}
```

---

**Browser:**
```
❌  WELCOME5 — Promo code not found.
```

---

**Shawon:** Gives up on promo codes. Clicks "Pay 20,250 THB."

---

## Step 2 — Payment Submission (No Promo Code)

---

**Browser:** Building payment request:

```
POST /api/v1/payments
Authorization:   Bearer eyJhbGciOiJIUzI1NiJ9...
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
Content-Type:    application/json

{
  "bookingId":       "booking-001-uuid",
  "paymentMethodId": "pm_1OaBC2LkdIwH",
  "amount":          20250.00,
  "currency":        "THB",
  "promoCode":       null
}
```

---

**PaymentService:** promoCode is null — skipping PromotionService.

Proceeding with amount 20,250 THB. Payment flows through Stripe
normally as in the happy path.

*(Full payment steps omitted — identical to happy path from here.)*

---

**Browser:** Payment succeeds. Confirmation rendered:

```
✅ Payment Successful

Grand Hyatt Bangkok
Deluxe King · Dec 20–25 · 2 guests
Amount charged:  20,250 THB
Card:            Visa ····4242
```

---

**Shawon:** Booked. Maybe next time the promo code will still be valid.

---

## Sub-case: What If Shawon Sends a Promo Code Directly in the Payment Request?

Some clients might skip the validate endpoint and submit the promo code
directly inside the payment body. PaymentService handles this too:

```json
POST /api/v1/payments
{
  "bookingId":       "booking-001-uuid",
  "paymentMethodId": "pm_1OaBC2LkdIwH",
  "amount":          18225.00,     ← Shawon self-calculated 10% off
  "currency":        "THB",
  "promoCode":       "SAVE10"
}
```

**PaymentService:** promoCode is present.

Calling PromotionService.validateAndApply("SAVE10", booking):

```
PromotionService: PROMO_EXPIRED → throws PromoExpiredException
```

**PaymentService:** Caught PromoExpiredException. Stripe is not called.
Amount in request (18,225) does not match booking.totalAmount (20,250) —
returning:

```json
HTTP/1.1 422 Unprocessable Entity

{
  "error":   "PROMO_EXPIRED",
  "message": "Promo code SAVE10 expired on 17 Dec 2024.",
  "hint":    "Remove the promo code to pay the full price."
}
```

The server never trusts the client-supplied amount — it always computes
the final amount itself.

---

## Key Design Decisions

**Why a separate /promotions/validate endpoint?**
Instant feedback without entering payment flow. Stripe is never invoked
for a promo error. User experience is cleaner — the error appears next
to the promo field, not the payment button.

**Why does PaymentService also validate the promo code, even after the
validate endpoint was called?**
The client is untrusted. Between validate and pay, the code could have
been deactivated, hit its usage limit, or the user could have crafted
a request with a stale code. Defence in depth: both layers validate.

**Why 422 Unprocessable Entity and not 400 Bad Request?**
400 means the request is syntactically malformed. 422 means the request
is syntactically correct but semantically invalid — the server understood
it but cannot act on it. An expired promo code is a semantic failure.

**Why not reveal the internal promotion_id in the error response?**
Security hygiene — internal IDs are not exposed to clients. The code
string (SAVE10) is sufficient for the user to understand what failed.

**Why stop validation at the first failed check?**
Fail-fast keeps logic simple and avoids leaking information. If the code
is expired, there is no reason to tell the user it was also over its
usage limit.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Enters promo code SAVE10, clicks Apply |
| 2 | Browser | POST /api/v1/promotions/validate |
| 3 | PromoController | Hand to PromotionService |
| 4 | PromotionRepository | SELECT promotion WHERE code = 'SAVE10' |
| 5 | PostgreSQL | Returns promo — valid_to = 2024-12-17 |
| 6 | PromotionService | valid_to < today → PromoExpiredException |
| 7 | PromoController | 422 PROMO_EXPIRED |
| 8 | Browser | Inline error — "expired on 17 Dec 2024" |
| 9 | Shawon | Tries WELCOME5 |
| 10 | PromotionRepository | SELECT — no rows |
| 11 | PromotionService | PromoNotFoundException |
| 12 | PromoController | 422 PROMO_NOT_FOUND |
| 13 | Browser | Inline error — "not found" |
| 14 | Shawon | Gives up, clicks Pay 20,250 THB |
| 15 | Browser | POST /api/v1/payments — promoCode: null |
| 16 | PaymentService | promoCode null — skips PromotionService |
| 17 | Stripe | Charges 20,250 THB — success |
| 18 | Browser | Renders confirmation |