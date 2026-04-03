# miniAgoda

A Java/Spring Boot hotel booking system modeled after Agoda, built as a
structured learning project with an explicit goal of evolving into a
distributed microservices architecture.

## What it does

miniAgoda allows users to search for available hotels by city, date range,
guest count, and room preferences, make bookings, write reviews, and manage
their account. Hotel owners can manage properties, room types, pricing, and
view operational data. Admins can moderate content and manage the platform.

## Project Status

**Current phase:** Design complete — implementation starting.

| Milestone | Status | Covers |
|---|---|---|
| MVP - Core Search | 🔄 Starting | Hotels, rooms, availability, search, users, auth |
| MVP - Core Booking | ⬜ Planned | Bookings, reviews, notifications, promotions |
| MVP - Admin | ⬜ Planned | Admin operations, moderation, system stats |
| MVP - Payment | ⬜ Planned | Payment gateway integration, refunds |

## Architecture

miniAgoda is a **modular monolith** — all services run in a single JVM,
but internal module boundaries mirror how the system would be split into
microservices. The design is intentionally microservices-ready from day one.

```
Client
  └── Spring Boot Application
        ├── Domain Services       (HotelSearchService, BookingService, ...)
        ├── Infrastructure Gateways (StorageGateway, EmailGateway, PaymentGateway)
        └── Repositories          (HotelRepository, BookingRepository, ...)
```

See [Architecture Overview](docs/architecture/overview.md) for the full picture.

## Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.8+ |
| Spring Boot | 3.x |

### Build & Run

```bash
git clone https://github.com/AhsanShafiqShawon/miniAgoda.git
cd miniAgoda
mvn clean install
mvn spring-boot:run
```

### Run Tests

```bash
mvn test
```

## Project Structure

```
miniAgoda/
├── src/
│   ├── main/java/com/miniagoda/
│   │   ├── commnon/
│   │   │     ├── config/
│   │   │     │     ├── SomeConfig.java
│   │   │     │     ├── SomeAnotherConfig.java
│   │   │     │
│   │   │     ├── exception/
│   │   │     │     ├── SomeException.java
│   │   │     │     ├── SomeAnotherException.java
│   │   │     │
│   │   │     ├── response/
│   │   │     │     ├── SomeResponse.java
│   │   │     │     ├── SomeAnotherResponse.java
│   │   │     │
│   │   │     ├── util/
│   │   │     │     ├── SomeUtil.java
│   │   │     │     ├── SomeAnotherUtil.java
│   │   │
│   │   ├── feature/
│   │   │     ├── FeatureController.java
│   │   │     ├── FeatureService.java
│   │   │     ├── FeatureRepository.java
│   │   │     ├── dto/
│   │   │     │     ├── FeatureRequest.java       ← records (immutable, no boilerplate)
│   │   │     │     ├── FeatureResponse.java      ← records
│   │   │     ├── entity/
│   │   │     │     ├── Feature.java              ← @Entity class (never a record)
│   │   │     │     ├── FeatureStatus.java        ← enum (lives here, not in entity/)
│   │   │     ├── exception/
│   │   │     │     ├── FeatureNotFoundException.java
│   │   │     ├── mapper/
│   │   │     │     ├── FeatureMapper.java
│   │
│   ├── test/java/com/miniagoda/
│   │   ├── feature1/
│   │   ├── feature2/
│   │   └── ...
│   │
│   └── main/resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__some_migration.sql
│           ├── V2__some_another_migration.sql
│           └── ...
├── docs/
│   ├── architecture/        # System design, domain model, ADRs
│   ├── api/                 # Service contracts
│   ├── setup/               # Getting started, configuration
│   ├── flows/
│   ├── wiki/
│   ├── http.md
│   ├── implementation-progress.md
│   └── roadmap.md
└── README.md
```

## Documentation

| Doc | Description |
|---|---|
| [Architecture Overview](docs/architecture/overview.md) | System design, layers, concurrency |
| [Domain Model](docs/architecture/domain-model.md) | All entities, value objects, enums |
| [ADR Index](docs/architecture/decisions/) | All architectural decisions with rationale |
| [Future Architecture](docs/architecture/future-microservices.md) | Target distributed design |
| [API Contracts](docs/api/) | All service contracts |
| [Setup Guide](docs/setup/getting-started.md) | Full setup instructions |
| [Roadmap](docs/roadmap.md) | Phase-by-phase evolution plan |

## Key Design Decisions

| Decision | ADR |
|---|---|
| `RatePolicy` as a separate class | [ADR-001](docs/architecture/decisions/ADR-001-rate-policy.md) |
| Availability modeled per room type | [ADR-002](docs/architecture/decisions/ADR-002-availability-per-room-type.md) |
| Concurrency via `ReentrantReadWriteLock` | [ADR-003](docs/architecture/decisions/ADR-003-concurrency.md) |
| `AvailabilityService` separate from `BookingService` | [ADR-004](docs/architecture/decisions/ADR-004-inventory-repository.md) |
| `RecommendationService` for insufficient results | [ADR-005](docs/architecture/decisions/ADR-005-recommendation-service.md) |
| Defer `RoomService` and physical room tracking | [ADR-006](docs/architecture/decisions/ADR-006-defer-room-service.md) |
| `AvailabilityService` with `AvailabilityRepository` | [ADR-007](docs/architecture/decisions/ADR-007-availability-service.md) |
| Spring Security for role-based authorization | [ADR-008](docs/architecture/decisions/ADR-008-spring-security-authorization.md) |
| `StorageGateway` abstraction | [ADR-009](docs/architecture/decisions/ADR-009-storage-service.md) |
| `EmailGateway` abstraction | [ADR-010](docs/architecture/decisions/ADR-010-email-service.md) |
| `PaymentGateway` abstraction | [ADR-011](docs/architecture/decisions/ADR-011-payment-gateway.md) |
| Infrastructure Gateway naming convention | [ADR-012](docs/architecture/decisions/ADR-012-gateway-naming-convention.md) |

---

*A personal learning project toward distributed systems and microservice architecture.*