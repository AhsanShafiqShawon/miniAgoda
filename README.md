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

miniAgoda is a modular monolith — all features run in a single JVM, but each feature module is self-contained and mirrors how the system would be split into microservices. The design is intentionally microservices-ready from day one.

```
Client
  └── Spring Boot Application
        ├── Feature Modules         (hotel/, booking/, user/, review/, ...)
        │     ├── Controller        (HTTP layer — routes requests in, sends responses out)
        │     ├── Service           (business logic)
        │     ├── Repository        (data access)
        │     ├── Mapper            (entity ↔ DTO conversion)
        │     ├── dto/              (request & response records)
        │     ├── entity/           (JPA entities & enums)
        │     ├── value/            (value objects)
        │     ├── exception/        (feature-scoped exceptions)
        │     └── gateway/          (external service abstractions, if needed)
        └── Common                  (shared config, exceptions, responses, utils)
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
│   │   ├── common/
│   │   │   ├── config/
│   │   │   │   ├── SomeConfig.java
│   │   │   │   └── SomeAnotherConfig.java
│   │   │   ├── filter/
│   │   │   │   ├── SomeFilter.java
│   │   │   │   └── SomeAnotherFilter.java
│   │   │   ├── exception/
│   │   │   │   ├── SomeException.java
│   │   │   │   └── SomeAnotherException.java
│   │   │   ├── response/
│   │   │   │   ├── SomeResponse.java
│   │   │   │   └── SomeAnotherResponse.java
│   │   │   ├── security/
│   │   │   │   ├── SomeSecurity.java
│   │   │   │   └── SomeAnotherSecurity.java
│   │   │   └── util/
│   │   │       ├── SomeUtil.java
│   │   │       └── SomeAnotherUtil.java
│   │   └── <feature>/
│   │       ├── FeatureController.java
│   │       ├── FeatureService.java
│   │       ├── FeatureRepository.java
│   │       ├── FeatureMapper.java
│   │       ├── dto/
│   │       │   ├── FeatureRequest.java               ← records (immutable, no boilerplate)
│   │       │   └── FeatureResponse.java              ← records
│   │       ├── entity/
│   │       │   ├── Feature.java                      ← @Entity class (never a record)
│   │       │   └── FeatureStatus.java                ← enum
│   │       ├── value/
│   │       │   └── someValueObjects.java             ← value object
│   │       ├── exception/
│   │       │   └── FeatureNotFoundException.java
│   │       └── gateway/                              # Only present if the feature calls an external service
│   │           ├── FeatureGateway.java               # Interface — the only thing the service imports
│   │           └── provider/
│   │               └── ProviderFeatureGateway.java   # Concrete implementation (Stripe, S3, SendGrid…)
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
├── prose/                   # Narrative of the code in plain English, i.e., code prose
├── docs/
│   ├── architecture/        # System design, domain model, ADRs
│   ├── api/                 # Service contracts
│   ├── setup/               # Getting started, configuration
│   ├── flows/               # How different types of request flows through the system
│   ├── wiki/                # Knowledge base
│   ├── conversation/        # How different module talks to each other
│   ├── http.md
│   ├── appendix.md
│   ├── implementation-progress.md
│   └── roadmap.md
├── .env                     # real secrets, gitignored
├── .env.example             # placeholders, committed
├── pom.xml
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
| Feature-Based Package Structure | [ADR-013](docs/architecture/decisions/ADR-013-feature-based-package-structure.md) |
| JWT Library Selection | [ADR-014](docs/architecture/decisions/ADR-014-jwt-library-selection.md) |
| JWT Authentication Filter | [ADR-015](docs/architecture/decisions/ADR-015-jwt-authentication-filter.md) |

---

*A personal learning project toward distributed systems and microservice architecture.*