# Future Microservices Architecture

## Target State

miniAgoda is designed to decompose into independently deployable services.
The module boundaries in the current monolith already reflect where service
splits will happen — switching from in-process calls to network calls is
the primary migration task.

---

## Proposed Service Map

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Search Service  │  │ Booking Service  │  │  Hotel Service   │
│                 │  │                 │  │                  │
│ HotelSearch     │  │ Booking         │  │ Hotel            │
│ Recommendation  │  │ Availability    │  │ RoomType         │
│ SearchHistory   │  │ HotelMgmt       │  │ Destination      │
└────────┬────────┘  └────────┬────────┘  └────────┬─────────┘
         │                    │                     │
┌────────▼────────┐  ┌────────▼────────┐  ┌────────▼─────────┐
│  User Service   │  │ Payment Service  │  │  Media Service   │
│                 │  │                 │  │                  │
│ User            │  │ Payment         │  │ Image            │
│ Auth            │  │ Refund          │  │ StorageGateway   │
└─────────────────┘  └─────────────────┘  └──────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Review Service  │  │  Notification   │  │  Admin Service  │
│                 │  │  Service        │  │                 │
│ Review          │  │ Notification    │  │ Admin           │
│ ReviewRating    │  │ EmailGateway    │  │ SystemStats     │
└─────────────────┘  └─────────────────┘  └─────────────────┘

                    ┌───────────────────┐
                    │  Promotion Service │
                    │                   │
                    │ Promotion         │
                    │ ValidatePromotion │
                    └───────────────────┘

         ┌──────────────────────────────────────┐
         │           Event Bus / Queue           │
         │                                      │
         │  booking.created   booking.cancelled │
         │  payment.completed review.submitted  │
         │  user.registered   search.recorded   │
         └──────────────────────────────────────┘
```

---

## Migration Path

### Phase 1 — Current: Modular Monolith

All services run in the same JVM. Module boundaries enforced by package
structure and interface definitions. No cross-module direct field access.

**Package structure mirrors service boundaries:**
```
com.miniagoda.search/
com.miniagoda.booking/
com.miniagoda.hotel/
com.miniagoda.user/
com.miniagoda.payment/
com.miniagoda.review/
com.miniagoda.notification/
com.miniagoda.promotion/
com.miniagoda.admin/
com.miniagoda.media/
```

### Phase 2 — Extract Search Service

Search is read-heavy and scales independently. No write path.

- `HotelSearchService`, `RecommendationService`, `SearchHistoryService`
- Reads from a denormalized availability view
- `AvailabilityService` becomes a read replica for search
- First extraction because it has zero write dependencies

### Phase 3 — Extract User & Auth Service

User identity is a foundational dependency for all other services.

- `UserService`, `AuthService`
- Issues JWT tokens consumed by all other services
- Spring Security configuration moves to an API Gateway

### Phase 4 — Extract Booking Service

Booking requires strong consistency — extract with care.

- `BookingService`, `AvailabilityService`, `HotelManagementService`
- Moves to a dedicated data store with optimistic locking
- Publishes `booking.created`, `booking.cancelled` events
- Other services subscribe to booking events

### Phase 5 — Extract Hotel & Media Service

Hotel data changes infrequently — good candidate for a cache layer.

- `HotelService`, `RoomTypeService`, `DestinationService`
- `ImageService` with `StorageGateway` → `S3StorageGateway`
- Serves as master data consumed by Search and Booking

### Phase 6 — Extract Payment Service

Payment is isolated by nature — clear boundary, external gateway dependency.

- `PaymentService` with `PaymentGateway` → `StripePaymentGateway`
- Stripe webhook handling moves to a dedicated webhook controller
- Publishes `payment.completed`, `payment.refunded` events

### Phase 7 — Extract Remaining Services

- `ReviewService` — subscribes to `booking.completed` for review requests
- `NotificationService` — subscribes to all events, sends emails
- `PromotionService` — consulted by Booking during checkout
- `AdminService` — aggregates data from all services

### Phase 8 — Multi-Region

- Search Service replicated across regions (eventual consistency acceptable)
- Booking Service with regional affinity (consistency critical)
- Distributed caching for hotel/room data
- Global load balancing and latency-based routing

---

## What Does Not Change

Across all phases, the **domain model** and **core business logic** stay
stable. The search and booking invariants defined in Phase 1 remain correct
regardless of whether they run in a monolith or a distributed system.

What changes is only the **transport** — in-process method calls become
network calls (REST or gRPC). The interfaces already exist. The logic
does not move.

The Infrastructure Gateways (`StorageGateway`, `EmailGateway`,
`PaymentGateway`) are already abstracted — their implementations swap
out without touching domain services.

---

## Key Preparedness

The monolith is designed so the microservices split requires:

| Task | Current State | Migration |
|---|---|---|
| Service boundaries | Package structure | Add network transport |
| Data stores | Shared in-memory | Dedicated DB per service |
| Concurrency | `ReentrantReadWriteLock` | DB-level / optimistic locking |
| Auth | Spring Security in-process | JWT validation at API Gateway |
| File storage | `LocalStorageGateway` | `S3StorageGateway` |
| Email | `SmtpEmailGateway` | `SendGridEmailGateway` |
| Payment | `StripePaymentGateway` | Already abstracted |
| Events | Direct method calls | Event bus (Kafka/RabbitMQ) |