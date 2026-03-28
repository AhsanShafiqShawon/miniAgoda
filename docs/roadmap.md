# Roadmap

## Overview

miniAgoda evolves through four milestones, each independently deliverable.
Every milestone builds on the previous — Core Search must be complete
before Core Booking, and so on.

---

## Milestone 1 — MVP: Core Search

**Goal:** A user can search for available hotels by city, date range,
guest count, and room preferences. Results are ranked, paginated, and
supplemented with recommendations when insufficient.

### Domain & Entities
- [ ] `Country` entity
- [ ] `City` entity
- [ ] `User` entity
- [ ] `Hotel` entity
- [ ] `RoomType` entity
- [ ] `RatePolicy` and `DiscountPolicy` value objects
- [ ] `Address`, `Coordinates`, `PhoneNumber` value objects
- [ ] `Image` entity
- [ ] `Destination` entity
- [ ] `SearchHistory` entity

### Services
- [ ] `UserService` — registration and profile management
- [ ] `AuthService` — JWT authentication, email verification, password management
- [ ] `HotelService` — hotel CRUD and lifecycle management
- [ ] `RoomTypeService` — room type and rate policy management
- [ ] `AvailabilityService` — room availability tracking
- [ ] `HotelSearchService` — city and hotel-level availability search
- [ ] `RecommendationService` — alternative suggestions for insufficient results
- [ ] `SearchHistoryService` — async search recording and history retrieval
- [ ] `DestinationService` — curated destinations and popularity tracking
- [ ] `ImageService` — image upload, confirmation, and retrieval

### Infrastructure
- [ ] `StorageGateway` — `LocalStorageGateway` implementation
- [ ] `EmailGateway` — `SmtpEmailGateway` implementation
- [ ] Spring Security configuration — JWT filter, role-based access

### Testing
- [ ] Unit tests for all services
- [ ] Integration tests for search flow

---

## Milestone 2 — MVP: Core Booking

**Goal:** A user can create, edit, and cancel bookings. Reviews can be
submitted after a completed stay. Hotel owners can view operational data.

### Domain & Entities
- [ ] `Booking` entity
- [ ] `Review` entity and `ReviewRating` value object
- [ ] `Notification` entity
- [ ] `Promotion` entity

### Services
- [ ] `BookingService` — create, edit, cancel, query bookings
- [ ] `ReviewService` — submit, edit, moderate reviews
- [ ] `HotelManagementService` — availability, revenue, occupancy queries
- [ ] `NotificationService` — booking confirmations, review requests
- [ ] `PromotionService` — promotional codes and discount validation

### Infrastructure
- [ ] `EmailGateway` — wired into `NotificationService`

### Testing
- [ ] Unit tests for all services
- [ ] Integration tests for booking and review flow
- [ ] Concurrency tests for double-booking prevention

---

## Milestone 3 — MVP: Admin

**Goal:** Admins can manage users, moderate content, and view system-wide
statistics and revenue.

### Services
- [ ] `AdminService` — user management, content moderation, system stats

### Testing
- [ ] Unit tests for all admin operations
- [ ] Integration tests for moderation flow

---

## Milestone 4 — MVP: Payment

**Goal:** Users can pay for bookings and receive refunds on cancellations.

### Domain & Entities
- [ ] `Payment` entity
- [ ] `Refund` entity

### Services
- [ ] `PaymentService` — payment processing and refund management

### Infrastructure
- [ ] `PaymentGateway` — `StripePaymentGateway` implementation
- [ ] Stripe webhook controller

### Testing
- [ ] Unit tests for payment lifecycle
- [ ] Integration tests with Stripe test environment

---

## Phase 2 — REST API Layer

**Goal:** Expose all service contracts as REST endpoints.

- [ ] Controllers for all services
- [ ] Request/response DTO mapping
- [ ] Input validation (`@Valid`, `@NotNull`, etc.)
- [ ] Error response shaping (`@ControllerAdvice`)
- [ ] API documentation (Swagger/OpenAPI)
- [ ] Integration tests against live endpoints

---

## Phase 3 — Persistence

**Goal:** Replace in-memory repositories with a real database.

- [ ] PostgreSQL setup
- [ ] JPA/Hibernate entity mappings
- [ ] Database migrations (Flyway)
- [ ] Replace `ReentrantReadWriteLock` with DB-level optimistic locking
- [ ] `S3StorageGateway` — replace `LocalStorageGateway`
- [ ] `SendGridEmailGateway` — replace `SmtpEmailGateway`

---

## Phase 4 — Microservices Split

**Goal:** Decompose the monolith into independently deployable services.

- [ ] Extract Search Service
- [ ] Extract User & Auth Service
- [ ] Extract Booking Service
- [ ] Extract Hotel & Media Service
- [ ] Extract Payment Service
- [ ] Extract Review, Notification, Promotion, Admin Services
- [ ] Introduce event bus (Kafka or RabbitMQ)
- [ ] API Gateway with JWT validation

See [future-microservices.md](architecture/future-microservices.md) for
the full 8-phase migration plan.

---

## Phase 5 — Multi-Region

**Goal:** Handle global traffic with low latency and high availability.

- [ ] Search Service deployed in multiple regions
- [ ] Booking Service with regional affinity and failover
- [ ] Distributed caching (Redis)
- [ ] Global load balancing and latency-based routing
- [ ] Multi-region PostgreSQL (read replicas)

---

## What Will Not Change

The domain model and core business logic designed in Milestone 1 remain
stable across all phases. Invariants established now hold true whether
the system runs as a monolith or across dozens of microservices.

What changes across phases is only the **transport** (in-process → network),
the **storage** (in-memory → PostgreSQL → distributed), and the
**deployment** (single JVM → multiple services).