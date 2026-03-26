# ADR-012: Infrastructure Gateway Naming Convention

## Status
Accepted

## Context

miniAgoda has multiple abstractions that decouple domain services from
external systems — file storage, email sending, and payment processing.
Initially `StorageService` and `EmailService` were used, but the `Service`
suffix implies business logic, which these abstractions do not contain.

A consistent naming convention is needed to distinguish infrastructure
abstractions from domain services.

## Decision

All infrastructure abstractions that wrap external systems use the
`Gateway` suffix:

| Abstraction | External System |
|---|---|
| `StorageGateway` | File storage (local filesystem → AWS S3) |
| `EmailGateway` | Email sending (SMTP → SendGrid/SES) |
| `PaymentGateway` | Payment processing (Stripe) |

The pattern:
- Defined as a Java `interface` — not a concrete class
- Multiple implementations selected via Spring `@Profile`
- No business logic — only translates calls to external systems
- Named `{ExternalConcept}Gateway`

```java
// Example pattern
public interface StorageGateway {
    String store(byte[] data, String fileName, String contentType);
    void delete(String fileKey);
    String getUrl(String fileKey);
}

@Service
@Profile("local")
public class LocalStorageGateway implements StorageGateway { ... }

@Service
@Profile("production")
public class S3StorageGateway implements StorageGateway { ... }
```

## Consequences

**Positive:**
- Clear distinction between domain services (`*Service`) and
  infrastructure abstractions (`*Gateway`)
- Consistent naming makes the codebase easier to navigate
- Any future external system integration follows the same pattern
- `Gateway` conveys "entry point to an external system" — accurate
  and well understood

**Negative:**
- `StorageService` and `EmailService` renamed — minor refactor needed
  in docs and code

## Future Gateways

New external system integrations should follow this pattern:
- SMS sending → `SmsGateway`
- Push notifications → `PushNotificationGateway`
- Analytics → `AnalyticsGateway`
- Maps/geocoding → `GeoGateway`

## Related Decisions

- [ADR-009](ADR-009-storage-service.md) — `StorageGateway` (renamed from `StorageService`)
- [ADR-010](ADR-010-email-service.md) — `EmailGateway` (renamed from `EmailService`)
- [ADR-011](ADR-011-payment-gateway.md) — `PaymentGateway`