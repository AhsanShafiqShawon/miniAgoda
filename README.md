# miniAgoda

A production-grade hotel booking REST API built with Java and Spring Boot, modeled after Agoda. The goal was to build something real — not a tutorial project — with serious attention to concurrency, security, clean architecture, and testability.

It started as a portfolio project to deepen backend engineering skills beyond what internships typically cover: designing for failure, handling race conditions under load, decoupling cross-cutting concerns, and building a system that could realistically grow into a distributed architecture.

---

## Technical Highlights

- **Double-booking prevention** — Pessimistic locking on inventory rows eliminates race conditions under concurrent load. Verified by a 50-thread concurrency test: zero double bookings, zero negative inventory, completed in 751ms.
- **Async booking confirmations** — Booking creation returns `202 Accepted` immediately. Confirmation emails are dispatched via Spring `@Async` with a dedicated thread pool, removing ~1,916ms of synchronous overhead from the critical path.
- **JWT auth with Redis denylist** — Access tokens are invalidated on logout via a Redis-backed denylist with 15-minute TTL, preventing revoked token reuse without a database call per request.
- **Gateway abstraction pattern** — Payment (Stripe) and notification (SendGrid) integrations are hidden behind interfaces, keeping business logic fully decoupled from third-party API contracts.
- **Role-based access control** — Four roles (GUEST, CUSTOMER, ADMIN, SUPER_ADMIN) enforced via Spring Security `@PreAuthorize`. Correct `401 vs 403` distinction across all protected endpoints.
- **91% service-layer coverage** — JaCoCo-verified across unit, integration, and gateway tests covering business logic, HTTP contracts, auth flows, error handling, and external integrations.
- **20 versioned schema migrations** — Flyway manages the full schema lifecycle, ensuring consistent data contracts across environments.

---

## Feature Status

| Module | Status | Covers |
|---|---|---|
| Search | ✅ Done | Hotel discovery, room types, availability |
| Booking | ✅ Done | Reservation creation, inventory management |
| Payment | ✅ Done | Payment gateway, transaction handling |
| Notification | ✅ Done | Booking confirmations, refund alerts |
| User | ✅ Done | Registration, profile management |
| Auth | ✅ Done | Authentication, role-based authorization |
| Refund | ✅ Done | Refund requests, gateway integration |

---

## Architecture

miniAgoda is a **modular monolith** — all features run in a single JVM, but each module is fully self-contained and mirrors how the system would be split into microservices. The design is intentionally microservices-ready from day one.

**Why a modular monolith?** Starting with a distributed system adds infrastructure complexity before the domain is well understood. This approach lets each module evolve independently (its own controller, service, repository, gateway, and exception hierarchy) without premature deployment overhead. When the time comes to extract a service, the boundary is already clean.

```
Client
  └── Spring Boot Application
        ├── Feature Modules         (hotel/, booking/, user/, review/, ...)
        │     ├── controller/       (HTTP layer — routes requests in, sends responses out)
        │     ├── service/          (business logic)
        │     ├── repository/       (data access)
        │     ├── mapper/           (entity ↔ DTO conversion)
        │     ├── dto/              (request & response records)
        │     ├── entity/           (JPA entities & enums)
        │     ├── value/            (value objects)
        │     ├── exception/        (feature-scoped exceptions)
        │     └── gateway/          (external service abstractions, if needed)
        └── Common                  (shared config, exceptions, responses, utils, filter, security)
```

---

## Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.8+ |
| Spring Boot | 3.x |
| PostgreSQL | 14+ |
| Redis | 7+ |

### Build & Run

```bash
git clone https://github.com/AhsanShafiqShawon/miniAgoda.git
cd miniAgoda
cp .env.example .env        # fill in your credentials
mvn clean install
mvn spring-boot:run
```

### Run with Docker

```bash
docker build -t miniagoda .
docker run --env-file .env -p 8080:8080 miniagoda
```

The image uses a multi-stage build — the final production image contains only the compiled JAR and the JRE.

### Run Tests

```bash
mvn test                    # unit + integration tests
mvn jacoco:report           # generate coverage report at target/site/jacoco/
```

---

## Configuration

Copy `.env.example` to `.env` and fill in the required values:

```env
DB_URL=
DB_USERNAME=
DB_PASSWORD=

STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=

JWT_SECRET=

REDIS_HOST=localhost
REDIS_PORT=6379

SENDGRID_API_KEY=
```

Flyway runs migrations automatically on startup. No manual schema setup needed.

---

## API Overview

Base URL: `http://localhost:8080/api`

### Auth — `/api/v1/auth`

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Register a new user |
| POST | `/api/v1/auth/login` | Login, returns access token |
| POST | `/api/v1/auth/logout` | Invalidate access token |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| GET | `/api/v1/auth/verify?token=` | Verify email address |

### Search

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/hotels/search` | Search hotels by city, dates, guests, room type |

### Hotels

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/hotels/{hotelId}` | Get hotel detail with room types |

### Bookings

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/booking` | Create a booking (returns 201) |
| GET | `/api/v1/bookings` | Get all bookings for the authenticated user |

### Payments

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/payments/create-intent` | Create a Stripe payment intent |
| POST | `/api/v1/payments/refund` | Request a refund |
| POST | `/api/v1/webhooks/stripe` | Stripe webhook handler |

---

## Testing Strategy

Tests are organized into three layers:

- **Unit tests** — Service logic in isolation, with mocked repositories and gateways.
- **Integration tests** — Full Spring context with an in-memory H2 database, verifying HTTP contracts end-to-end.
- **Gateway tests** — Mock Stripe and SendGrid clients verifying correct request construction and error handling.

Coverage is enforced via JaCoCo at 91% service-layer line coverage. The concurrency test (`BookingConcurrencyTest`) simulates 50 simultaneous booking requests against a single inventory slot to verify pessimistic locking holds under load.

---

## Project Structure

<details>
<summary>Full file tree</summary>

```
miniAgoda/
├── src/
│   ├── main/java/com/miniagoda/
│   │   ├── common/
│   │   │   ├── seed/DataSeeder.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── AppConfig.java
│   │   │   ├── exception/GlobalExceptionHandler.java
│   │   │   └── entity/BaseEntity.java
│   │   ├── search/
│   │   │   ├── controller/SearchController.java
│   │   │   ├── service/SearchService.java
│   │   │   └── dto/
│   │   │       ├── SearchRequest.java
│   │   │       └── SearchResponse.java
│   │   ├── hotel/
│   │   │   ├── controller/HotelController.java
│   │   │   ├── repository/
│   │   │   │   ├── HotelRepository.java
│   │   │   │   └── RoomTypeRepository.java
│   │   │   ├── service/HotelService.java
│   │   │   ├── entity/
│   │   │   │   ├── Hotel.java
│   │   │   │   └── RoomType.java
│   │   │   └── dto/
│   │   │       ├── HotelDetailRequest.java
│   │   │       ├── HotelDetailResponse.java
│   │   │       ├── RoomTypeResponse.java
│   │   │       └── RoomTypeSeed.java
│   │   ├── inventory/
│   │   │   ├── repository/InventoryRepository.java
│   │   │   ├── service/InventoryService.java
│   │   │   ├── exception/InventoryUnavailableException.java
│   │   │   └── entity/Inventory.java
│   │   ├── booking/
│   │   │   ├── controller/BookingController.java
│   │   │   ├── service/BookingService.java
│   │   │   ├── repository/BookingRepository.java
│   │   │   ├── exception/
│   │   │   │   ├── NotEnoughRoomsException.java
│   │   │   │   ├── InventoryIncompleteException.java
│   │   │   │   └── BookingNotFoundException.java
│   │   │   ├── entity/
│   │   │   │   ├── Booking.java
│   │   │   │   └── BookingStatus.java
│   │   │   └── dto/
│   │   │       ├── BookingRequest.java
│   │   │       └── BookingResponse.java
│   │   ├── payment/
│   │   │   ├── controller/PaymentController.java
│   │   │   ├── repository/PaymentRepository.java
│   │   │   ├── service/PaymentService.java
│   │   │   ├── exception/PaymentAlreadyExistException.java
│   │   │   ├── entity/
│   │   │   │   ├── Payment.java
│   │   │   │   └── PaymentStatus.java
│   │   │   ├── dto/
│   │   │   │   ├── PaymentIntentRequest.java
│   │   │   │   ├── PaymentIntentResponse.java
│   │   │   │   ├── RefundRequest.java
│   │   │   │   ├── RefundResponse.java
│   │   │   │   ├── PaymentGatewayRequest.java
│   │   │   │   └── RefundGatewayRequest.java
│   │   │   ├── gateway/
│   │   │   │   ├── PaymentGateway.java
│   │   │   │   ├── PaymentEvent.java
│   │   │   │   └── stripe/StripeGateway.java
│   │   │   └── config/
│   │   │       ├── StripeConfig.java
│   │   │       └── PaymentGatewayConfig.java
│   │   ├── user/
│   │   │   ├── repository/UserRepository.java
│   │   │   ├── exception/UserNotFoundException.java
│   │   │   └── entity/
│   │   │       ├── User.java
│   │   │       └── Role.java
│   │   ├── auth/
│   │   │   ├── controller/AuthController.java
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java
│   │   │   │   └── UserDetailsServiceImpl.java
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthFilter.java
│   │   │   │   ├── UserDetailsImpl.java
│   │   │   │   └── UserDetailsServiceImpl.java
│   │   │   ├── util/
│   │   │   │   ├── JwtUtil.java
│   │   │   │   └── VerificationTokenUtil.java
│   │   │   ├── listener/NotificationEventListener.java
│   │   │   ├── exception/
│   │   │   │   ├── EmailAlreadyExistException.java
│   │   │   │   ├── InvalidRefreshTokenException.java
│   │   │   │   ├── VerificationTokenNotFoundException.java
│   │   │   │   ├── TokenAlreadyUsedException.java
│   │   │   │   └── TokenHasExpiredException.java
│   │   │   ├── entity/
│   │   │   │   ├── RefreshToken.java
│   │   │   │   └── EmailVerificationToken.java
│   │   │   ├── repository/
│   │   │   │   ├── RefreshTokenRepository.java
│   │   │   │   └── EmailVerificationTokenRepository.java
│   │   │   └── dto/
│   │   │       ├── RegisterRequest.java
│   │   │       ├── RegisterResponse.java
│   │   │       ├── LoginRequest.java
│   │   │       └── LoginResponse.java
│   │   ├── notification/
│   │   │   ├── config/
│   │   │   │   ├── NotificationProperties.java
│   │   │   │   ├── NotificationConfig.java
│   │   │   │   └── NotificationAsyncConfig.java
│   │   │   ├── exception/NotificationException.java
│   │   │   ├── gateway/
│   │   │   │   ├── EmailGateway.java
│   │   │   │   └── sendGrid/SendGridEmailGateway.java
│   │   │   ├── service/NotificationService.java
│   │   │   ├── dto/EmailMessage.java
│   │   │   ├── template/EmailTemplateRenderer.java
│   │   │   └── event/
│   │   │       ├── BookingConfirmed.java
│   │   │       ├── BookingCancelled.java
│   │   │       ├── PaymentSuccess.java
│   │   │       ├── PaymentFailureEvent.java
│   │   │       ├── AccountRegisteredEvent.java
│   │   │       ├── BookingConfirmedNotificationEvent.java
│   │   │       ├── PaymentSuccessNotificationEvent.java
│   │   │       ├── BookingCancelledNotificationEvent.java
│   │   │       ├── AccountRegisteredNotificationEvent.java
│   │   │       └── PaymentFailureNotificationEvent.java
│   │   └── MiniAgodaApplication.java
│   ├── test/java/com/miniagoda/
│   └── main/resources/
│       ├── application.yml
│       ├── data/room_types.json
│       └── db/migration/
│           ├── V1__init_schema.sql
│           ├── V2__add_rating_and_hotel_code_to_hotels.sql
│           ├── V3__seed_bangkok_hotels.sql
│           ├── V4__alter_room_type_rename_total_rooms_to_capacity_add_total_units.sql
│           ├── V5__alter_hotel_rename_hotel_code_to_code.sql
│           ├── V6__update_inventory_table.sql
│           ├── V7__add_city_to_hotels.sql
│           ├── V8__create_bookings_table.sql
│           ├── V9__add_expired_at_to_bookings.sql
│           ├── V10__create_payments_table.sql
│           ├── V11__alter_payments_rename_stripe_payment_intent_id_to_payment_token.sql
│           ├── V12__add_currency_to_bookings.sql
│           ├── V13__create_users_table.sql
│           ├── V14__add_user_id_to_bookings.sql
│           ├── V15__seed_super_admin.sql
│           ├── V16__create_refresh_tokens_table.sql
│           ├── V17__alter_refresh_tokens_rename_token_to_token_hash.sql
│           ├── V18__add_verified_to_users.sql
│           ├── V19__create_email_verification_tokens_table.sql
│           └── V20__add_inventory_check_constraint.sql
├── docs/
│   └── miniAgoda.postman_collection.json
├── .env.example
├── Dockerfile
├── pom.xml
└── README.md
```

</details>

---

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x, Spring Security, Spring Data JPA |
| Database | PostgreSQL |
| Cache / Token store | Redis |
| Migrations | Flyway |
| ORM | Hibernate / JPA |
| Build | Maven |
| Containerization | Docker (multi-stage) |
| Payment gateway | Stripe |
| Email gateway | SendGrid |
| Testing | JUnit 5, JaCoCo |