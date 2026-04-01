# Payment Scenario 3: Duplicate Payment Attempt — Idempotency Replay

**User:** Shawon (authenticated)
**Booking:** Grand Hyatt Bangkok — Deluxe King, Dec 20–25, 2 guests, 1 room
**Situation:** Shawon clicks "Pay Now", the network stalls, the browser
shows a spinner. Shawon panics and clicks "Pay Now" again — or the
browser auto-retries. Two identical requests reach the server.
**Outcome:** Charged exactly once. Second request returns the same result
as the first without hitting Stripe again.

---

## The Cast

| Character | Layer | Role |
|---|---|---|
| `Shawon` | Client | The user |
| `Browser` | Client | Sends request — then retries on timeout |
| `API Gateway` | Infrastructure | First line of idempotency defence |
| `Load Balancer` | Infrastructure | May route retries to a different instance |
| `PaymentController` | Application | Maps HTTP to service call |
| `PaymentService` | Domain | Orchestrates payment, checks idempotency |
| `PaymentRepository` | Data | Reads existing payment by idempotency key |
| `PaymentGatewayClient` | Infrastructure | Passes idempotency key to Stripe |
| `Stripe (External)` | External | Deduplicates on its own idempotency key |
| `PostgreSQL` | Database | Source of truth for payment state |

---

## The Conversation

---

**Shawon:** Clicks "Pay Now."

---

**Browser:** Building and sending Request A:

```
POST /api/v1/payments
Authorization:   Bearer eyJhbGciOiJIUzI1NiJ9...
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1   ← generated once, stored locally
Content-Type:    application/json

{
  "bookingId":       "booking-001-uuid",
  "paymentMethodId": "pm_1OaBC2LkdIwH",
  "amount":          20250.00,
  "currency":        "THB"
}
```

Request A sent. Waiting for response...

*(3 seconds pass — no response. Network stalled.)*

Browser timeout reached. Shawon is still on the payment page.
Resending with the **same** Idempotency-Key — Request B:

```
POST /api/v1/payments
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1   ← identical key
...identical body...
```

---

## Request A — Racing Through the System

---

**API Gateway (Request A):**
```
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
Cache lookup:    MISS — not seen before
Action:          Proceed, store key with TTL 24h
```

Forwarding Request A to app-2 (least connections at this moment).

---

**PaymentService (Request A):**

Booking validated ✅. Room available ✅.

**Step 1 — Check own idempotency store:**
```sql
SELECT payment_id, status
FROM   payments
WHERE  idempotency_key = 'idem-7f3a2b1c-4d5e-6f7a-pay1';
```

---

**PostgreSQL:** No row found — first time seeing this key.

---

**PaymentService (Request A):** Key is new.

Inserting PENDING record:
```sql
INSERT INTO payments (payment_id, ..., idempotency_key, status)
VALUES ('pay-001-uuid', ..., 'idem-7f3a2b1c-4d5e-6f7a-pay1', 'PENDING');
```

Calling PaymentGatewayClient → Stripe with same idempotency key.

*(Stripe is processing — takes ~2 seconds.)*

---

## Request B — Arriving While Request A Is Still In-Flight

---

**API Gateway (Request B):**
```
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
Cache lookup:    HIT — key seen, but no cached response yet
                 (Request A hasn't returned)
Action:          Key is in-flight — return 409 Locked immediately
```

```json
HTTP/1.1 409 Conflict
Retry-After: 3

{
  "error":   "IDEMPOTENCY_KEY_IN_FLIGHT",
  "message": "A request with this key is already being processed. Retry after 3 seconds."
}
```

Browser receives this. Waits 3 seconds. Will retry.

---

## Request A — Completes Successfully

---

**Stripe:** Payment authorised. Returning success to PaymentGatewayClient.

---

**PaymentService (Request A):**

```sql
UPDATE payments SET status = 'COMPLETED', ... WHERE payment_id = 'pay-001-uuid';
UPDATE bookings  SET status = 'CONFIRMED', ... WHERE booking_id = 'booking-001-uuid';
```

Both committed. ✅

---

**API Gateway:** Receiving Request A's response. Caching it against the
idempotency key with TTL 24h:

```
idem-7f3a2b1c-4d5e-6f7a-pay1 →
  {
    "paymentId": "pay-001-uuid",
    "status":    "COMPLETED",
    ...
  }
  cached until: 2024-12-19T09:48:00Z
```

---

**Browser (Request A):** Receives 200 COMPLETED — but the original
tab already showed a spinner and this response was lost due to the
timeout. Browser is now waiting for Request B's retry.

---

## Request B — Retried After 3 Seconds

---

**Browser:** Retrying Request B after Retry-After delay:

```
POST /api/v1/payments
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
...identical body...
```

---

**API Gateway (Request B retry):**
```
Idempotency-Key: idem-7f3a2b1c-4d5e-6f7a-pay1
Cache lookup:    HIT — cached response exists now
Action:          Return cached response immediately
                 Stripe is NOT called again
                 PaymentService is NOT called again
```

```json
HTTP/1.1 200 OK
X-Idempotency-Replayed: true

{
  "paymentId":   "pay-001-uuid",
  "bookingId":   "booking-001-uuid",
  "status":      "COMPLETED",
  "amount":      20250.00,
  "currency":    "THB",
  "paidAt":      "2024-12-18T09:48:33Z",
  "last4":       "4242",
  "cardBrand":   "VISA"
}
```

---

**Browser:** Received 200 COMPLETED (replayed). Rendering confirmation:

```
✅ Payment Successful

Grand Hyatt Bangkok
Deluxe King · Dec 20–25 · 2 guests
Amount charged:  20,250 THB
Card:            Visa ····4242
Booking ref:     booking-001-uuid
```

---

**Shawon:** Sees confirmation. Does not know there were two requests —
and was charged exactly once.

---

## What If Request B Had Slipped Past the API Gateway?

If the API Gateway cache had missed (e.g. Load Balancer routed Request B
to a different instance before the cache propagated), PaymentService has
a second idempotency guard at the database layer:

```sql
SELECT payment_id, status
FROM   payments
WHERE  idempotency_key = 'idem-7f3a2b1c-4d5e-6f7a-pay1';
```

This returns `pay-001-uuid` with status `COMPLETED`.

**PaymentService:** Key already processed and completed. Returning
existing result without calling Stripe.

This is the **double-guard pattern** — API Gateway handles the common
case, the database handles the rare case.

---

## What If Both Requests Reached PaymentService Simultaneously?

The `INSERT INTO payments` uses a unique constraint on `idempotency_key`:

```sql
ALTER TABLE payments
  ADD CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key);
```

If two threads race to insert the same key:
- Thread A succeeds — inserts the row
- Thread B gets a `UniqueConstraintViolationException`
- Thread B catches the exception, re-reads the row, and returns the
  existing result

No double charge is possible.

---

## Key Design Decisions

**Why does the Browser reuse the same Idempotency-Key on retry?**
The key must be generated once per *intent* — not per *request*. If the
browser generated a new key on each retry, every attempt would look like
a fresh payment to the server.

**Why does Stripe also receive the idempotency key?**
Stripe has its own deduplication layer. If miniAgoda's server crashed
after calling Stripe but before recording the result, a retry with the
same key would return Stripe's cached response — avoiding a second charge
even if our DB has no record of the first attempt.

**Why cache at the API Gateway and also guard in the DB?**
The API Gateway cache is fast but lives in memory — it can miss during
restarts or cross-instance routing. The DB constraint is the ultimate
safety net.

---

## Summary

| # | Character | Action |
|---|---|---|
| 1 | Shawon | Clicks Pay Now |
| 2 | Browser | Sends Request A with Idempotency-Key |
| 3 | API Gateway | Key not seen — proceeds, stores key |
| 4 | PaymentService (A) | DB idempotency check — no row yet |
| 5 | PaymentRepository | INSERT PENDING with idempotency key |
| 6 | PaymentGatewayClient | Calls Stripe with same key |
| 7 | Browser | Timeout — sends Request B (same key) |
| 8 | API Gateway (B) | Key in-flight — returns 409 Locked |
| 9 | Stripe | Authorises payment for Request A |
| 10 | PaymentService (A) | UPDATE COMPLETED, booking CONFIRMED |
| 11 | API Gateway | Caches completed response against key |
| 12 | Browser | Waits 3 seconds, retries Request B |
| 13 | API Gateway (B retry) | Key found in cache — returns cached 200 |
| 14 | Stripe | Never called a second time ✅ |
| 15 | Browser | Renders confirmation — Shawon charged once |