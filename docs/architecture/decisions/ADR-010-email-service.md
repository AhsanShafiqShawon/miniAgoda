# ADR-010: EmailService Abstraction for Email Sending

## Status
Accepted

## Context

`NotificationService` needs to send emails. The simplest approach is to
use Spring's `JavaMailSender` directly inside `NotificationService`.
However, this creates a hard dependency on JavaMail that would need to
be rewritten when moving to a transactional email provider (e.g. SendGrid,
AWS SES, Mailgun) in production.

## Decision

Introduce an `EmailService` interface that abstracts the email sending
backend. `NotificationService` depends on `EmailService`, not on any
specific email implementation.

```java
public interface EmailService {
    void send(String to, String subject, String body);
}
```

Two implementations:

```java
// Current phase — JavaMailSender (SMTP)
@Service
@Profile("local")
public class SmtpEmailService implements EmailService { ... }

// Future phase — SendGrid or AWS SES
@Service
@Profile("production")
public class SendGridEmailService implements EmailService { ... }
```

Spring profiles control which implementation is active. Switching from
SMTP to SendGrid requires no changes to `NotificationService` — only
the active profile changes.

## Consequences

**Positive:**
- `NotificationService` is decoupled from email implementation
- Switching to SendGrid or SES requires no changes to `NotificationService`
- SMTP works for development and testing
- Clean interface — easy to mock in tests
- Consistent with [ADR-009](ADR-009-storage-service.md) (`StorageService` abstraction pattern)

**Negative:**
- One additional abstraction layer
- SMTP is not suitable for production at scale — limited deliverability,
  no tracking, no bounce handling

## Migration Path

Phase 1 (current): `SmtpEmailService` — emails sent via SMTP/JavaMailSender
Phase 2 (production): `SendGridEmailService` — emails sent via SendGrid API
  with delivery tracking, bounce handling, and email templates

## Alternatives Considered

- **Use `JavaMailSender` directly in `NotificationService`**: Hard
  dependency on SMTP. Rejected in favour of abstraction.
- **Use SendGrid from the start**: Requires API key setup and external
  dependency. Deferred to production phase.

## Related Decisions

- [ADR-009](ADR-009-storage-service.md) — same abstraction pattern
  applied to file storage