# API Contract: Payment

## Overview

`PaymentService` is responsible for processing payments for bookings,
managing refunds, and integrating with the payment gateway. It is NOT
responsible for booking creation (that's `BookingService`), sending
payment confirmation emails (that's `NotificationService`), or
promotions (that's `PromotionService`).

See [ADR-011](../architecture/decisions/ADR-011-payment-gateway.md) for
the PaymentGateway abstraction rationale.

## Collaborators

```java
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentGateway paymentGateway;
}
```

## Payment Lifecycle

```
PENDING → CONFIRMED → COMPLETED
        → CANCELLED
COMPLETED → REFUNDED (via processRefund)
```

## Stripe Webhook Integration

Stripe sends async webhook events that trigger status transitions:
- `payment_intent.confirmed` → `confirmPayment()`
- `payment_intent.succeeded` → `completePayment()`
- `refund.created` → updates `Refund.status` to `COMPLETED`

Webhooks are handled by a dedicated `StripeWebhookController`.

---

## Methods

### `makePayment(CreatePaymentRequest request)`

Initiates a payment. Creates a `PENDING` payment record and calls
the payment gateway to create a transaction.

```java
Payment makePayment(CreatePaymentRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Look up booking — throw `ResourceNotFoundException` if not found
3. Call `paymentGateway.initiatePayment()` — returns `gatewayTransactionId`
4. Create `Payment` with status `PENDING`, store `gatewayTransactionId`
5. Set `gatewayProvider` to active gateway (e.g. `"STRIPE"`)
6. Persist and return

---

### `confirmPayment(UUID paymentId)`

Confirms a payment after gateway confirmation. Triggered by webhook.

```java
void confirmPayment(UUID paymentId);
```

**Behavior:**
1. Look up payment — throw `ResourceNotFoundException` if not found
2. Check status is `PENDING` — throw `InvalidPaymentStateException` if not
3. Call `paymentGateway.confirmPayment(gatewayTransactionId)`
4. Update status to `CONFIRMED`, set `updatedAt`

---

### `completePayment(UUID paymentId)`

Marks payment as completed after funds settle. Triggered by webhook.

```java
void completePayment(UUID paymentId);
```

**Behavior:**
1. Look up payment — throw `ResourceNotFoundException` if not found
2. Check status is `CONFIRMED` — throw `InvalidPaymentStateException` if not
3. Update status to `COMPLETED`, set `updatedAt`
4. Call `NotificationService.sendNotification()` async — payment confirmation

---

### `cancelPayment(UUID paymentId)`

Cancels a pending payment before completion.

```java
void cancelPayment(UUID paymentId);
```

**Behavior:**
1. Look up payment — throw `ResourceNotFoundException` if not found
2. Check status is `PENDING` or `CONFIRMED` — throw `InvalidPaymentStateException` if not
3. Call `paymentGateway.cancelPayment(gatewayTransactionId)`
4. Update status to `CANCELLED`, set `updatedAt`

---

### `processRefund(UUID paymentId)`

Initiates a full refund for a completed payment.

```java
Refund processRefund(UUID paymentId);
```

**Behavior:**
1. Look up payment — throw `ResourceNotFoundException` if not found
2. Check status is `COMPLETED` — throw `InvalidPaymentStateException` if not
3. Call `paymentGateway.processRefund()` — returns `gatewayRefundId`
4. Create `Refund` with status `PENDING`, store `gatewayRefundId`
5. Update `Payment.status` to `REFUNDED`
6. Persist and return `Refund`

---

### `getPaymentById(UUID paymentId)`

Returns a single payment by ID.

```java
Payment getPaymentById(UUID paymentId);
```

---

### `getPaymentsByBooking(UUID bookingId)`

Returns all payments for a booking. A booking with multiple room types
(via `bookingGroupId`) may have multiple payments.

```java
List<Payment> getPaymentsByBooking(UUID bookingId);
```

---

### `getAllPaymentsByUser(UUID userId, int page, int size)`

Returns all payments made by a user, paginated.

```java
List<Payment> getAllPaymentsByUser(UUID userId, int page, int size);
```

---

### `getAllPaymentsByHotel(UUID hotelId, int page, int size)`

Returns all payments for bookings at a specific hotel, paginated.

```java
List<Payment> getAllPaymentsByHotel(UUID hotelId, int page, int size);
```

---

### `getAllPayments(int page, int size)`

Admin-only. Returns all payments regardless of status, paginated.

```java
List<Payment> getAllPayments(int page, int size);
```

---

### `getPaymentsByStatus(PaymentStatus status, int page, int size)`

Returns all payments filtered by status, paginated.

```java
List<Payment> getPaymentsByStatus(PaymentStatus status, int page, int size);
```

---

### `getRefundByPayment(UUID paymentId)`

Returns the refund associated with a payment.

```java
Refund getRefundByPayment(UUID paymentId);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Payment not found | `ResourceNotFoundException` |
| Booking not found | `ResourceNotFoundException` |
| Invalid status transition | `InvalidPaymentStateException` |
| Gateway failure | `PaymentGatewayException` |
| Refund on non-COMPLETED payment | `InvalidPaymentStateException` |