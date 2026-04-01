# Payment Scenario 4: Stripe Webhook Delayed / Out-of-Order

**User:** Shawon (authenticated)
**Booking:** Grand Hyatt Bangkok — Deluxe King, Dec 20–25
**Situation:** Shawon completes 3DS and Stripe charges the card. But
Stripe's webhook is delayed by 4 minutes due to a Stripe-side delivery
issue. Shawon's browser redirects back to miniAgoda and polls for the
result — finding a payment stuck in REQUIRES_ACTION. The system must
handle this gracefully without showing Shawon a false failure — and
reconcile correctly once the webhook eventually arrives.
**Outcome:** Shawon sees a "pending" state briefly, webhook arrives
and confirms, Shawon gets her confirmation.

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Redirected back from 3DS, polls for status |
| `PaymentController` | Application | Status endpoint + webhook handler |
| `PaymentService` | Domain | Reads status, processes webhook |
| `PaymentGatewayClient` | Infrastructure | Can also query Stripe directly |
| `PaymentRepository` | Data | Reads / updates payment record |
| `BookingService` | Domain | Confirms booking on payment success |
| `NotificationService` | Domain | Dispatches confirmation async |
| `Stripe (External)` | External | Charged the card — webhook delayed |
| `PostgreSQL` | Database | Stores payment state |

---

## Background: Normal Webhook Timing

Under normal conditions:
```
Stripe charges card → webhook fires within 1–2 seconds
Browser redirect arrives at miniAgoda → polls status → COMPLETED
```

In this scenario:
```
Stripe charges card → webhook delayed (Stripe infrastructure blip)
Browser redirect arrives → polls status → still REQUIRES_ACTION
```

---

## The Conversation

---

**Shawon:** Completes 3DS OTP. Stripe charges card successfully at
09:48:00Z. Stripe queues the webhook — but delivery stalls.

---

**Stripe (External):** Card charged at 09:48:00Z.
```
PaymentIntent: pi_3OaXY2LkdIwH
Status:        succeeded
Amount:        2,025,000 satang
Auth code:     KBK-482917-AUTH
```

Webhook queued for `https://api.miniagoda.com/api/v1/webhooks/stripe`.
First delivery attempt fails (connection timeout on Stripe's side).
Stripe will retry with exponential backoff: 09:50, 09:52, 09:56...

---

**Browser:** Stripe.js 3DS flow completes. Redirecting to return_url:

```
GET https://miniagoda.com/payment/callback
  ?payment_intent=pi_3OaXY2LkdIwH
  &redirect_status=succeeded
```

---

**Browser:** On callback page. Polling payment status:

```
GET /api/v1/payments/pay-001-uuid
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

**PaymentController:** Fetching payment pay-001-uuid.

PaymentService, fetch current status.

---

**PaymentRepository:**
```sql
SELECT status, gateway_ref, paid_at
FROM   payments
WHERE  payment_id = 'pay-001-uuid';
```

**PostgreSQL:** Returns `status = REQUIRES_ACTION`. Webhook has not
arrived yet.

---

**PaymentController:** Serialising response:

```json
HTTP/1.1 200 OK

{
  "paymentId": "pay-001-uuid",
  "status":    "REQUIRES_ACTION",
  "message":   "Payment is being verified. Please wait..."
}
```

---

**Browser:** Status is still REQUIRES_ACTION. Not showing an error —
showing a waiting state:

```
⏳  Verifying your payment...

We're confirming your payment with the bank.
This usually takes a few seconds.
```

Browser will poll again in 3 seconds.

---

## First Active Reconciliation Poll (09:48:05Z)

Browser polls again. Status still REQUIRES_ACTION from DB.

**PaymentService:** DB says REQUIRES_ACTION — but the redirect_status
from Stripe's return_url says `succeeded`. This mismatch triggers an
**active reconciliation** — rather than waiting indefinitely for the
webhook, PaymentService queries Stripe directly.

---

**PaymentGatewayClient:** Querying Stripe for latest PaymentIntent state:

```
GET https://api.stripe.com/v1/payment_intents/pi_3OaXY2LkdIwH
Authorization: Bearer sk_live_miniagoda_stripe_key
```

---

**Stripe (External):** Returning current PaymentIntent:

```json
{
  "id":     "pi_3OaXY2LkdIwH",
  "status": "succeeded",
  "amount": 2025000,
  "charges": {
    "data": [{
      "id":                "ch_3OaXY2LkdIwH",
      "amount_captured":   2025000,
      "authorization_code": "KBK-482917-AUTH"
    }]
  }
}
```

---

**PaymentGatewayClient:** Stripe confirms `succeeded`. Returning to
PaymentService.

---

**PaymentService:** Stripe API confirms payment succeeded. DB still shows
REQUIRES_ACTION. Treating this as equivalent to receiving the webhook —
processing completion now.

**Step 1 — Update payment to COMPLETED:**

```sql
UPDATE payments
SET    status             = 'COMPLETED',
       gateway_charge_id  = 'ch_3OaXY2LkdIwH',
       auth_code          = 'KBK-482917-AUTH',
       reconciled_via     = 'STRIPE_API_POLL',   ← audit trail
       paid_at            = NOW(),
       updated_at         = NOW()
WHERE  payment_id         = 'pay-001-uuid'
  AND  status             = 'REQUIRES_ACTION';
```

---

**PostgreSQL:** Row updated. COMPLETED.

---

**PaymentService:** Payment completed via reconciliation poll.

**Step 2 — Confirm booking:**

```sql
UPDATE bookings
SET    status       = 'CONFIRMED',
       payment_id   = 'pay-001-uuid',
       confirmed_at = NOW()
WHERE  booking_id   = 'booking-001-uuid'
  AND  status       = 'PENDING_PAYMENT';
```

**Step 3 — Deduct availability:**

```sql
INSERT INTO availability_blocks
    (room_type_id, booking_id, check_in, check_out)
VALUES
    ('rt-001-uuid', 'booking-001-uuid', '2024-12-20', '2024-12-25');
```

**Step 4 — Dispatch notifications async:**

NotificationService sends confirmation email + push.

---

**PaymentController:** Returning COMPLETED status to Browser's next poll.

```json
HTTP/1.1 200 OK

{
  "paymentId":   "pay-001-uuid",
  "status":      "COMPLETED",
  "amount":      20250.00,
  "currency":    "THB",
  "paidAt":      "2024-12-18T09:48:07Z",
  "last4":       "4242",
  "cardBrand":   "VISA"
}
```

---

**Browser:** Received COMPLETED. Rendering confirmation:

```
✅ Payment Successful

Grand Hyatt Bangkok
Deluxe King · Dec 20–25 · 2 guests
Amount charged:  20,250 THB
```

*(Total wait: ~7 seconds instead of the usual 1–2. Shawon saw a
brief "verifying" spinner but no error.)*

---

**Shawon:** Sees confirmation. Everything looks fine.

---

## Webhook Arrives 4 Minutes Later (09:52:00Z)

Stripe retries webhook delivery. This time it succeeds.

---

**PaymentController (Webhook handler):**

```json
POST /api/v1/webhooks/stripe

{
  "type": "payment_intent.succeeded",
  "data": { "object": { "id": "pi_3OaXY2LkdIwH", "status": "succeeded", ... } }
}
```

Stripe signature verified ✅. Handing to PaymentService.

---

**PaymentService:** Processing `payment_intent.succeeded`.

Checking current DB state:

```sql
UPDATE payments
SET    status = 'COMPLETED', ...
WHERE  payment_id = 'pay-001-uuid'
  AND  status     = 'REQUIRES_ACTION';   ← guard
```

---

**PostgreSQL:** 0 rows affected — status is already COMPLETED.
No duplicate processing. Returning.

---

**PaymentService:** Already completed — this webhook is a no-op.
Returning to PaymentController.

---

**PaymentController:** Responding to Stripe:

```json
HTTP/1.1 200 OK
```

Stripe marks webhook as delivered. Will not retry again.

---

## What If Reconciliation Had Not Run?

If PaymentService had not polled Stripe after the mismatch, Shawon
would have seen the "verifying" spinner indefinitely. To prevent
this, a fallback scheduler also runs:

```
PaymentReconciliationScheduler — runs every 5 minutes

Finds all payments WHERE:
  status     = 'REQUIRES_ACTION'
  updated_at < NOW() - INTERVAL '5 minutes'

For each: queries Stripe API, applies result (COMPLETED or FAILED)
```

This catches any case the active poll also missed (e.g. browser closed
before the poll triggered).

---

## Key Design Decisions

**Why poll Stripe on status mismatch instead of waiting for the webhook?**
Webhooks are best-effort and can be delayed minutes or hours. Relying
solely on webhooks for user-facing confirmation leads to stuck states.
The active poll gives a guaranteed resolution path independent of webhook
delivery.

**Why keep the guard `AND status = 'REQUIRES_ACTION'` on all UPDATEs?**
Without it, the delayed webhook could overwrite a FAILED status with
COMPLETED — if somehow the payment failed after reconciliation but before
webhook arrival (extremely unlikely, but defensive coding prevents it).

**Why log `reconciled_via = 'STRIPE_API_POLL'`?**
Audit trail. Finance teams can distinguish between normal webhook-driven
completions and reconciliation-driven ones. Useful for debugging and
monthly reconciliation reports.

**Why does Stripe retry webhooks?**
Stripe retries failed webhooks up to 72 hours with exponential backoff.
miniAgoda must always return 200 OK quickly — even for no-ops — or
Stripe will keep retrying.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Completes 3DS OTP |
| 2 | Stripe | Card charged at 09:48:00Z — webhook delivery stalls |
| 3 | Browser | Redirected back to callback page |
| 4 | Browser | Polls GET /api/v1/payments/pay-001-uuid |
| 5 | PaymentRepository | Returns status = REQUIRES_ACTION |
| 6 | Browser | Shows "verifying" spinner — polls again in 3s |
| 7 | PaymentService | Detects redirect_status=succeeded but DB=REQUIRES_ACTION |
| 8 | PaymentGatewayClient | Queries Stripe API directly |
| 9 | Stripe | Returns PaymentIntent status = succeeded |
| 10 | PaymentRepository | UPDATE COMPLETED (reconciled_via=STRIPE_API_POLL) |
| 11 | BookingService | UPDATE booking = CONFIRMED |
| 12 | AvailabilityRepository | INSERT availability_blocks |
| 13 | NotificationService | Send confirmation async |
| 14 | Browser | Next poll returns COMPLETED — renders confirmation |
| 15 | Shawon | Sees confirmation after ~7s total |
| 16 | Stripe (09:52) | Webhook finally delivered |
| 17 | PaymentService | UPDATE guard hits 0 rows — no-op |
| 18 | PaymentController | 200 OK to Stripe — no retry needed |