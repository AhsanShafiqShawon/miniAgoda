# Payment Scenario 6: Payment Gateway Timeout — Stripe Unreachable

**User:** Shawon (authenticated)
**Booking:** Grand Hyatt Bangkok — Deluxe King, Dec 20–25
**Situation:** Shawon submits payment. PaymentGatewayClient calls Stripe,
but Stripe does not respond within the timeout window. The card may or
may not have been charged — we do not know. The system must not show
Shawon a false success, must not leave her booking in a broken state,
and must reconcile the true outcome as soon as possible.
**Outcome:** Shawon sees a "payment is taking longer than expected"
message. A reconciliation job resolves the outcome within minutes.

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Submitted payment, now polling for result |
| `PaymentController` | Application | Payment endpoint + status endpoint |
| `PaymentService` | Domain | Orchestrates payment, handles timeout |
| `PaymentGatewayClient` | Infrastructure | Calls Stripe — receives no response |
| `PaymentReconciliationScheduler` | Domain | Background job — resolves ambiguous payments |
| `PaymentRepository` | Data | Reads / updates payment record |
| `BookingService` | Domain | Confirms or cancels booking post-reconciliation |
| `NotificationService` | Domain | Informs Shawon of outcome |
| `Stripe (External)` | External | May or may not have charged the card |
| `PostgreSQL` | Database | Stores payment state |

---

## Background: The Danger Zone

A gateway timeout is the most dangerous payment failure mode. Unlike a
clean decline (card rejected — definitive) or a network error before
the call (nothing was sent — safe to retry), a timeout means:

```
We sent the request to Stripe.
We do not know if Stripe received it.
We do not know if the card was charged.
Retrying blindly could double-charge Shawon.
Not retrying could leave a successful charge unacknowledged.
```

The system must treat a timeout as **ambiguous** — not failed, not
succeeded — and resolve it through reconciliation.

---

## The Conversation

---

**Shawon:** Clicks "Pay Now."

---

**Browser:** Sends payment request:

```
POST /api/v1/payments
Authorization:   Bearer eyJhbGciOiJIUzI1NiJ9...
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
Content-Type:    application/json

{
  "bookingId":       "booking-001-uuid",
  "paymentMethodId": "pm_1OaBC2LkdIwH",
  "amount":          20250.00,
  "currency":        "THB"
}
```

---

**PaymentService:** Booking validated ✅. Room available ✅.

**Step 1 — Insert PENDING record:**

```sql
INSERT INTO payments (payment_id, ..., idempotency_key, status)
VALUES ('pay-001-uuid', ..., 'idem-7f3a2b1c-4d5e-6f7a-pay1', 'PENDING');
```

PostgreSQL: committed. ✅

**Step 2 — Call Stripe:**

PaymentGatewayClient, charge pm_1OaBC2LkdIwH for 20,250 THB.

---

**PaymentGatewayClient:** Sending to Stripe:

```
POST https://api.stripe.com/v1/payment_intents
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
...
```

Waiting for response...

*(5 seconds pass — TCP connection open, no bytes received)*
*(10 seconds pass — still waiting)*
*(30 seconds — configured timeout threshold reached)*

```
java.net.SocketTimeoutException: Read timed out after 30000ms
  at PaymentGatewayClient.charge(PaymentGatewayClient.java:84)
```

We sent the request. We do not know what Stripe did with it.

---

**PaymentGatewayClient:** Throwing GatewayTimeoutException to
PaymentService. NOT retrying — retrying without knowing the first
attempt's outcome could double-charge.

---

**PaymentService:** Caught GatewayTimeoutException.

**Step 3 — Update payment to GATEWAY_TIMEOUT:**

```sql
UPDATE payments
SET    status         = 'GATEWAY_TIMEOUT',
       gateway_ref    = 'pi_3OaXY2LkdIwH',   ← PaymentIntent ID we sent
       timeout_at     = NOW(),
       updated_at     = NOW()
WHERE  payment_id     = 'pay-001-uuid'
  AND  status         = 'PENDING';
```

Note: We store the PaymentIntent ID we generated before the call. Even
though Stripe didn't respond, this ID was in our request — Stripe may
have processed it under this ID. We will query this exact ID during
reconciliation.

---

**PostgreSQL:** Row updated. status = GATEWAY_TIMEOUT. Returning.

---

**PaymentService:** Returning ambiguous outcome to PaymentController.

**Booking is NOT cancelled.** Status remains PENDING_PAYMENT —
we do not know if the charge succeeded. Cancelling would be wrong if
the card was already charged.

---

**PaymentController:** Serialising response:

```json
HTTP/1.1 202 Accepted
Content-Type: application/json

{
  "paymentId": "pay-001-uuid",
  "status":    "GATEWAY_TIMEOUT",
  "message":   "Your payment is taking longer than expected. We are checking with your bank. Please do not submit again.",
  "pollUrl":   "/api/v1/payments/pay-001-uuid"
}
```

*(202 Accepted — not 500. The request was accepted and is being
processed; outcome just isn't known yet.)*

---

**Browser:** Received 202 with GATEWAY_TIMEOUT status. Rendering:

```
⏳  Your payment is being verified

We're confirming with your bank.
This may take a few minutes.

Please do not click Pay again — your card
will not be charged twice.

We'll notify you as soon as it's confirmed.

[ Check Status ]
```

---

**Shawon:** Anxious — but the message reassures her not to retry.
She waits.

---

## Background: PaymentReconciliationScheduler

A scheduled job runs every 2 minutes, looking for payments in ambiguous
states:

```java
@Scheduled(fixedDelay = 120_000)
public void reconcileAmbiguousPayments() { ... }
```

---

**PaymentReconciliationScheduler** *(runs at 09:50:00Z, ~2 minutes
after the timeout)*:

Scanning for unresolved payments:

```sql
SELECT payment_id, gateway_ref, idempotency_key, booking_id
FROM   payments
WHERE  status   IN ('GATEWAY_TIMEOUT', 'PENDING')
  AND  created_at < NOW() - INTERVAL '1 minute';   ← give Stripe time
```

**PostgreSQL:**
```
payment_id:       pay-001-uuid
gateway_ref:      pi_3OaXY2LkdIwH
idempotency_key:  idem-7f3a2b1c-4d5e-6f7a-pay1
booking_id:       booking-001-uuid
```

Returning 1 unresolved payment.

---

**PaymentReconciliationScheduler:** Querying Stripe for the real outcome.

PaymentGatewayClient, fetch PaymentIntent pi_3OaXY2LkdIwH.

---

**PaymentGatewayClient:**

```
GET https://api.stripe.com/v1/payment_intents/pi_3OaXY2LkdIwH
Authorization: Bearer sk_live_miniagoda_stripe_key
```

*(Stripe is back — outage resolved.)*

---

**Stripe (External):** Returning PaymentIntent:

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

Stripe did process the original request. Card was charged.

---

**PaymentReconciliationScheduler:** Stripe confirms `succeeded`.

**Step 1 — Update payment to COMPLETED:**

```sql
UPDATE payments
SET    status             = 'COMPLETED',
       gateway_charge_id  = 'ch_3OaXY2LkdIwH',
       auth_code          = 'KBK-482917-AUTH',
       reconciled_via     = 'RECONCILIATION_SCHEDULER',
       paid_at            = NOW(),
       updated_at         = NOW()
WHERE  payment_id         = 'pay-001-uuid'
  AND  status             = 'GATEWAY_TIMEOUT';
```

**Step 2 — Confirm booking:**

```sql
UPDATE bookings
SET    status       = 'CONFIRMED',
       confirmed_at = NOW()
WHERE  booking_id   = 'booking-001-uuid'
  AND  status       = 'PENDING_PAYMENT';
```

**Step 3 — Deduct availability:**

```sql
INSERT INTO availability_blocks (room_type_id, booking_id, check_in, check_out)
VALUES ('rt-001-uuid', 'booking-001-uuid', '2024-12-20', '2024-12-25');
```

**Step 4 — Notify Shawon:**

NotificationService (async):

```
Email subject: Your booking is confirmed — Grand Hyatt Bangkok
Push:          ✅ Booking Confirmed · Grand Hyatt · Dec 20–25 · 20,250 THB
```

---

**NotificationService:** Dispatching email and push to Shawon.

---

**Shawon:** Receives push notification ~2 minutes after the timeout.

```
✅ Booking Confirmed

Grand Hyatt Bangkok
Deluxe King · Dec 20–25 · 2 guests
20,250 THB charged to Visa ····4242
```

Opens app — sees confirmation. Relief.

---

## What If Stripe Had NOT Charged the Card?

If Stripe's response had been `status: "requires_payment_method"` or
the PaymentIntent was not found:

**PaymentReconciliationScheduler:**

```sql
UPDATE payments
SET    status         = 'FAILED',
       failure_reason = 'GATEWAY_TIMEOUT_NO_CHARGE',
       reconciled_via = 'RECONCILIATION_SCHEDULER',
       updated_at     = NOW()
WHERE  payment_id     = 'pay-001-uuid'
  AND  status         = 'GATEWAY_TIMEOUT';

UPDATE bookings
SET    status      = 'PENDING_PAYMENT',   ← stays open — Shawon can retry
       updated_at  = NOW()
WHERE  booking_id  = 'booking-001-uuid';
```

NotificationService sends:
```
Push: ⚠️ Payment unsuccessful — please try again
      Your booking hold is still active for 10 more minutes.
```

Shawon can return and pay with a different card or retry.

---

## Key Design Decisions

**Why GATEWAY_TIMEOUT status and not FAILED?**
FAILED implies a definitive outcome. GATEWAY_TIMEOUT signals ambiguity
to every other part of the system — the scheduler knows to reconcile it,
support knows what happened, and the booking is kept alive rather than
cancelled.

**Why store the PaymentIntent ID before the call, not after?**
The ID is generated by miniAgoda and sent to Stripe (not returned by
Stripe). Storing it beforehand means we can query Stripe by this ID
even if we never received Stripe's acknowledgement.

**Why not retry the charge immediately?**
Without knowing the first attempt's result, retrying could create a
second charge. The idempotency key protects against this IF Stripe
received both requests — but if Stripe's timeout was caused by a partial
write, behaviour is unpredictable. Query first, act on confirmed state.

**Why 202 Accepted and not 500 Internal Server Error?**
500 tells the browser the request failed. 202 tells it the request was
received and is being processed. This prevents the browser from showing
a generic error page and encourages Shawon to wait rather than retry.

**Why keep the booking PENDING_PAYMENT during reconciliation?**
Cancelling is irreversible. If we cancel and then discover the charge
succeeded, we would need to issue a refund and rebook — complex and
bad UX. Keeping it PENDING costs nothing and preserves Shawon's hold.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Clicks Pay Now |
| 2 | PaymentService | Booking validated, room available |
| 3 | PaymentRepository | INSERT PENDING with gateway_ref |
| 4 | PaymentGatewayClient | POST to Stripe — no response after 30s |
| 5 | PaymentGatewayClient | SocketTimeoutException — not retrying |
| 6 | PaymentRepository | UPDATE status = GATEWAY_TIMEOUT |
| 7 | PaymentController | 202 Accepted — "verifying with bank" |
| 8 | Browser | Shows "please wait" — warns not to retry |
| 9 | Shawon | Waits anxiously |
| 10 | PaymentReconciliationScheduler | Runs at +2 minutes |
| 11 | PostgreSQL | Returns pay-001-uuid with GATEWAY_TIMEOUT |
| 12 | PaymentGatewayClient | GET pi_3OaXY2LkdIwH from Stripe |
| 13 | Stripe | Returns status = succeeded — card was charged |
| 14 | PaymentRepository | UPDATE COMPLETED (reconciled_via=SCHEDULER) |
| 15 | BookingService | UPDATE booking = CONFIRMED |
| 16 | AvailabilityRepository | INSERT availability_blocks |
| 17 | NotificationService | Push + email to Shawon |
| 18 | Shawon | Receives push — booking confirmed ✅ |