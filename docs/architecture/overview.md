# Architecture Overview

## Design Philosophy

miniAgoda is built in phases with a clear destination: a distributed,
multi-region microservices architecture. The current version is a
**modular monolith** — all logic runs in a single JVM, but internal
boundaries are drawn to mirror how the system would be split if it
were a set of microservices.

This means:
- Domain boundaries are respected even within a single process
- No cross-cutting service calls outside defined boundaries
- State is never held in static fields
- Interfaces defined at every service boundary
- Infrastructure concerns abstracted behind Gateway interfaces

---

## System Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                    REST Controllers                             │
│           (Phase 2 — not yet implemented)                       │
├─────────────────────────────────────────────────────────────────┤
│                    Domain Services                              │
│                                                                 │
│  HotelSearchService        BookingService        ReviewService  │
│  HotelService              RoomTypeService       UserService    │
│  AuthService               ImageService          AdminService   │
│  SearchHistoryService      DestinationService                   │
│  RecommendationService     NotificationService                  │
│  HotelManagementService    PromotionService                     │
│  AvailabilityService       PaymentService                       │
├─────────────────────────────────────────────────────────────────┤
│                    Repositories                                 │
│                                                                 │
│  HotelRepository           BookingRepository                    │
│  AvailabilityRepository    UserRepository                       │
│  ReviewRepository          NotificationRepository               │
│  SearchHistoryRepository   DestinationRepository                │
│  PromotionRepository       PaymentRepository                    │
│  RefundRepository          ImageRepository                      │
│  TokenRepository                                                │
├─────────────────────────────────────────────────────────────────┤
│               Infrastructure Gateways                           │
│                                                                 │
│  StorageGateway            EmailGateway          PaymentGateway │
└─────────────────────────────────────────────────────────────────┘
```

---

## Domain Services

Domain services own business logic. Each service has a single,
well-defined responsibility. They communicate via constructor-injected
dependencies — never via static calls or shared mutable state.

| Service | Responsibility |
|---|---|
| `HotelSearchService` | Search available hotels and room types |
| `HotelService` | Hotel data management — CRUD, activate/deactivate |
| `RoomTypeService` | Room type and rate policy management |
| `AvailabilityService` | Room availability tracking and inventory management |
| `BookingService` | Guest-facing booking operations |
| `ReviewService` | Guest reviews and hotel rating updates |
| `HotelManagementService` | Hotel operational queries — revenue, occupancy, availability |
| `UserService` | User registration and profile management |
| `AuthService` | Authentication, JWT token management, password management |
| `SearchHistoryService` | User search history recording and retrieval |
| `DestinationService` | Curated destinations and popularity tracking |
| `RecommendationService` | Alternative hotel suggestions for insufficient results |
| `NotificationService` | Email notifications for booking, auth, and review events |
| `ImageService` | Image upload, management, and retrieval |
| `PromotionService` | Promotional codes and discount management |
| `AdminService` | System-wide admin operations and content moderation |
| `PaymentService` | Payment processing and refund management |

---

## Infrastructure Gateways

Infrastructure Gateways abstract external systems behind a Java interface.
Domain services depend on the interface — not the concrete implementation.
Implementations are selected via Spring `@Profile`.

See [ADR-012](decisions/ADR-012-gateway-naming-convention.md) for the
naming convention rationale.

| Gateway | Interface | Current Implementation | Future |
|---|---|---|---|
| `StorageGateway` | File storage | `LocalStorageGateway` | `S3StorageGateway` |
| `EmailGateway` | Email sending | `SmtpEmailGateway` | `SendGridEmailGateway` |
| `PaymentGateway` | Payment processing | `StripePaymentGateway` | Multi-gateway |

---

## Concurrency Model

The system is designed for meaningful concurrent load — multiple users
searching and booking simultaneously without data races.

Key decisions:
- `AvailabilityRepository` uses a `ReentrantReadWriteLock` — concurrent
  reads, exclusive writes. See [ADR-003](decisions/ADR-003-concurrency.md).
- `AvailabilityService.blockRooms()` and `releaseRooms()` are internal
  methods — only `BookingService` calls them, inside a write-locked
  critical section. See [ADR-007](decisions/ADR-007-availability-service.md).
- All domain objects are immutable Java records — thread-safe by design.
- Async operations (`@Async`) used for non-blocking side effects:
  search history recording, notification sending, count increments.

---

## Authorization Model

Role-based access control is handled by Spring Security annotations
(`@PreAuthorize`) at the controller layer — not by explicit service
method checks. See [ADR-008](decisions/ADR-008-spring-security-authorization.md).

| Role | Access |
|---|---|
| `GUEST` | Search, book, write reviews, manage own profile |
| `HOTEL_ADMIN` | Full hotel management — data and operations |
| `HOTEL_MANAGER` | Operational access — bookings and availability |
| `SUPER_ADMIN` | System-wide access — all operations |

Anonymous users can search but cannot book. Handled at the security layer —
no `ANONYMOUS` role exists in the domain.

---

## Key Design Decisions

All significant architectural decisions are recorded as ADRs in
[docs/architecture/decisions/](decisions/). Key decisions:

- [ADR-001](decisions/ADR-001-rate-policy.md) — `RatePolicy` as a separate class
- [ADR-002](decisions/ADR-002-availability-per-room-type.md) — Availability per room type
- [ADR-006](decisions/ADR-006-defer-room-service.md) — Physical room tracking deferred
- [ADR-009](decisions/ADR-009-storage-service.md) — `StorageGateway` abstraction
- [ADR-011](decisions/ADR-011-payment-gateway.md) — `PaymentGateway` abstraction

---

## Related Docs

- [Domain Model](domain-model.md) — all entities, value objects, enums
- [Future Architecture](future-microservices.md) — microservices target state
- [API Contracts](../../docs/api/) — all service contracts