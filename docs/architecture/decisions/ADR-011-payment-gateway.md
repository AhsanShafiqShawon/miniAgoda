# ADR-011: PaymentGateway Abstraction for Payment Processing

## Status
Accepted

## Context

`PaymentService` needs to process payments via a payment gateway. The
simplest approach is to use Stripe's SDK directly inside `PaymentService`.
However, this creates a hard dependency on Stripe that would need to be
rewritten if switching to another provider (e.g. PayPal, Braintree,
Adyen) in the future.

Additionally, direct Stripe SDK calls inside `PaymentService` make
unit testing difficult — tests would need to mock Stripe's SDK classes
rather than a clean interface.

## Decision

Introduce a `PaymentGateway` interface that abstracts the payment
processing backend. `PaymentService` depends on `PaymentGateway`,
not on any specific gateway implementation.

```java
public interface PaymentGateway {
    String initiatePayment(BigDecimal amount, String currencyCode,
                           PaymentMethod method);    // returns gatewayTransactionId
    void confirmPayment(String gatewayTransactionId);
    void cancelPayment(String gatewayTransactionId);
    String processRefund(String gatewayTransactionId,
                         BigDecimal amount);         // returns gatewayRefundId
}
```

One implementation for MVP:

```java
// MVP — Stripe
@Service
@Profile("payment")
public class StripePaymentGateway implements PaymentGateway { ... }
```

Spring profiles control which implementation is active. Switching to
another gateway requires no changes to `PaymentService` — only a new
`PaymentGateway` implementation.

## Consequences

**Positive:**
- `PaymentService` is decoupled from Stripe SDK
- Switching payment providers requires no changes to `PaymentService`
- Clean interface — easy to mock in unit tests
- Consistent with ADR-009 (`StorageService`) and ADR-010 (`EmailService`)
  abstraction pattern
- `gatewayProvider` field on `Payment` entity supports future
  multi-gateway scenarios

**Negative:**
- One additional abstraction layer
- Stripe-specific features (webhooks, payment intents) need to be
  mapped to the generic interface

## Migration Path

Phase 1 (MVP): `StripePaymentGateway` — payments via Stripe API
Phase 2 (future): Additional gateway implementations as needed
  (PayPal, Braintree, regional providers)

## Stripe Webhook Handling

Stripe sends asynchronous webhook events (payment confirmed, refund
processed, etc.). These are handled by a dedicated `StripeWebhookController`
that calls `PaymentService.confirmPayment()` or `completePayment()`
based on the event type. Webhook handling is outside the scope of
`PaymentGateway` interface.

## Alternatives Considered

- **Use Stripe SDK directly in `PaymentService`**: Hard dependency on
  Stripe. Rejected in favour of abstraction.
- **Use a payment abstraction library**: Adds third-party dependency.
  A simple interface is sufficient for current needs.

## Related Decisions

- [ADR-009](ADR-009-storage-service.md) — StorageService abstraction
- [ADR-010](ADR-010-email-service.md) — EmailService abstraction