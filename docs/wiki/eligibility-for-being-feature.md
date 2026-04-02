# Domain Services vs Feature Modules

> A service is not the same as a feature module.
> This is the guide for deciding when a domain concept becomes its own module and when it lives inside another.

---

## The Two Meanings of "Service"

In a Spring Boot project, the word "service" means two different things and it is important to never confuse them.

| Term | What it means | Example |
|------|---------------|---------|
| **Domain service** | A business capability in your application design | `BookingService`, `PaymentService` |
| **Service class** | A Java class annotated with `@Service` inside a module | `BookingService.java` in `booking/` |

Domain services are design-level concepts. Service classes are implementation-level code. A domain service becomes one or more service classes — but not necessarily its own module.

---

## The Core Question

Before creating a new feature module, ask:

> Does this domain concept have its own database table, its own endpoints, and its own business logic?

```
Own entity + own endpoints + own business logic  →  own feature module
No endpoints, or shares an entity with another   →  service class inside another module
```

If a domain concept only exists to support another concept — it belongs inside that concept's module as a class, not as its own folder.

---

## Applied to miniAgoda's 17 Domain Services

```
Domain Service            Module                Reason
─────────────────────────────────────────────────────────────────────────────
AuthService               auth/                 Own endpoints, own token entity
UserService               user/                 Own entity, own endpoints
HotelService              hotel/                Core entity, own endpoints
RoomTypeService           hotel/                No standalone endpoints — lives inside hotel/
BookingService            booking/              Own entity, complex business logic
PaymentService            booking/              No standalone endpoints — part of booking flow
AvailabilityService       hotel/                No own entity — a component inside hotel/
HotelSearchService        search/               Own endpoints, own logic
SearchHistoryService      search/               Lightweight — lives inside search/
DestinationService        hotel/                Supporting data for hotels
HotelManagementService    hotel/                Same entity as HotelService, host-facing logic
ReviewService             review/               Own entity, own endpoints
RecommendationService     recommendation/       Own endpoints, distinct logic
NotificationService       notification/         Own entity, own endpoints
PromotionService          promotion/            Own entity, own endpoints
AdminService              admin/                Cross-cutting, own endpoints
ImageService              hotel/                No own endpoints — storage utility inside hotel/
```

---

## 17 Domain Services → 8 Feature Modules

```
src/main/java/com/miniagoda/
├── common/
├── auth/
├── user/
├── hotel/                ← HotelService, RoomTypeService, AvailabilityService,
│                            DestinationService, HotelManagementService, ImageService
├── search/               ← HotelSearchService, SearchHistoryService
├── booking/              ← BookingService, PaymentService
├── review/
├── recommendation/
├── notification/
├── promotion/
├── admin/
└── MiniAgodaApplication.java
```

---

## What a Multi-Service Module Looks Like Inside

When multiple domain services collapse into one module, they become **separate service classes** inside it — not separate sub-modules. The `hotel/` module is the best example of this.

```
hotel/
├── HotelController.java             guest-facing reads
├── HotelManagementController.java   host-facing writes
├── HotelService.java                guest-facing logic
├── HotelManagementService.java      host-facing logic
├── AvailabilityService.java         availability checks and updates
├── RoomTypeService.java             room type operations
├── DestinationService.java          destination lookups
├── ImageService.java                image storage utility
├── dto/
│   ├── HotelResponse.java
│   ├── HotelRequest.java
│   ├── RoomTypeResponse.java
│   ├── RoomTypeRequest.java
│   ├── AvailabilityRequest.java
│   └── DestinationResponse.java
├── entity/
│   ├── Hotel.java
│   ├── RoomType.java
│   ├── Availability.java
│   ├── Destination.java
│   └── HotelStatus.java
├── exception/
│   ├── HotelNotFoundException.java
│   └── RoomUnavailableException.java
├── mapper/
│   ├── HotelMapper.java
│   └── RoomTypeMapper.java
└── repository/
    ├── HotelRepository.java
    ├── RoomTypeRepository.java
    ├── AvailabilityRepository.java
    └── DestinationRepository.java
```

Same feature checklist applies — each service class still follows the full bottom-up order. The module just contains more than one of each layer.

---

## Why `PaymentService` Lives Inside `booking/`

This is the most common question about this design. The reasoning is worth spelling out explicitly.

`PaymentService` has no standalone CRUD endpoints. There is no `GET /payments`, no `POST /payments` in isolation. A payment only ever happens in the context of creating or cancelling a booking. The lifecycle is:

```
POST /bookings
  → BookingService.create()
      → PaymentService.charge()       ← called internally, never directly
  → return booking with payment status
```

If `PaymentService` were its own module, every booking operation would reach across module boundaries to trigger payment logic. That creates tight coupling between two modules and makes the booking flow harder to follow and test.

By living inside `booking/`, `PaymentService` is an internal collaborator — an implementation detail of how bookings work, not a public-facing feature.

The same logic applies to `ImageService` (always called in the context of managing a hotel) and `AvailabilityService` (always queried in the context of a hotel or booking).

---

## The Boundary Decision — Full Reasoning

| Situation | Decision | Why |
|-----------|----------|-----|
| Has its own entity and its own endpoints | Own module | Fully independent lifecycle |
| Has its own entity but no endpoints | Own module still, but no controller | Entity needs its own repository and migrations |
| Shares an entity with another domain | Lives inside that domain's module | Splitting would duplicate entity access |
| Has endpoints but no entity (pure logic) | Own module with no entity folder | Logic is still distinct enough to isolate |
| Called only internally by one other service | Class inside that service's module | It is an implementation detail, not a feature |
| Called internally by many services | `common/` utility or its own module | Shared dependency belongs in a shared place |

---

## The Rule of Thumb

```
Standalone endpoints + own entity   →  own feature module
No endpoints, or shares an entity   →  service class inside another module
Called by many modules              →  common/ utility
```

---

## All 8 Modules at a Glance

| Module | Domain services inside | Has controller? |
|--------|------------------------|-----------------|
| `auth/` | AuthService | Yes |
| `user/` | UserService | Yes |
| `hotel/` | HotelService, RoomTypeService, AvailabilityService, DestinationService, HotelManagementService, ImageService | Yes (2 controllers) |
| `search/` | HotelSearchService, SearchHistoryService | Yes |
| `booking/` | BookingService, PaymentService | Yes |
| `review/` | ReviewService | Yes |
| `recommendation/` | RecommendationService | Yes |
| `notification/` | NotificationService | Yes |
| `promotion/` | PromotionService | Yes |
| `admin/` | AdminService | Yes |