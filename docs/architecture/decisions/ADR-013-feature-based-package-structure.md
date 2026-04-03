# ADR-013: Feature-Based Package Structure

## Status
Accepted

## Context

The initial project structure organised code by technical layer:

```
src/main/java/com/miniagoda/
├── controller/
├── service/
├── repository/
└── db/
```

This is the default layout most Spring Boot tutorials use and it works
for small projects. As miniAgoda grew to 17 services, this structure
created two problems.

**Problem 1 — A single change touches many folders.**
Adding a new feature (e.g. promotion) meant creating files across four
separate directories. There was no single place to look at everything
related to promotions — the controller was in `controller/`, the service
in `service/`, the entity in a separate package. Navigation required
jumping across the project constantly.

**Problem 2 — Package boundaries communicate nothing.**
`controller/` tells you the technical role of the class but nothing
about what domain it belongs to. `PromotionController` and
`PaymentController` sitting in the same package have no relationship
with each other — yet they appeared as neighbours. Cohesion was low.

## Decision

Reorganise the project around **features**, not technical layers.
Each feature owns all of its classes in one place:

```
src/main/java/com/miniagoda/
├── common/
│   ├── config/          ← AppConfig, SecurityConfig, JwtConfig
│   ├── exception/       ← GlobalExceptionHandler
│   ├── response/        ← ApiResponse<T>, ErrorResponse
│   └── util/            ← JwtUtil
│
└── <feature>/
    ├── FeatureController.java
    ├── FeatureService.java
    ├── FeatureRepository.java
    ├── dto/
    │   ├── FeatureRequest.java      ← record
    │   └── FeatureResponse.java     ← record
    ├── entity/
    │   ├── Feature.java             ← @Entity class
    │   └── FeatureStatus.java       ← enum
    ├── exception/
    │   └── FeatureNotFoundException.java
    └── mapper/
        └── FeatureMapper.java
```

Cross-cutting infrastructure that belongs to no single feature lives
in `common/`. Everything else lives inside its feature package.

Two additional conventions are adopted alongside this restructure:

**DTOs are records.** Request and response DTOs are immutable data
carriers — records are the correct Java type for this. Entities remain
regular classes because JPA requires mutability, a no-args constructor,
and subclassability for proxy generation. See ADR-001 for the record
vs entity distinction as it applied to `RatePolicy`.

**Feature-local exceptions.** Each feature defines its own exceptions
(e.g. `BookingExpiredException` in `booking/exception/`). Only
exceptions that are genuinely cross-cutting live in `common/exception/`.

## Consequences

**Positive:**
- All code for a feature lives in one folder — adding or changing a
  feature requires touching one place, not four
- Package names communicate domain intent — `booking/` immediately
  tells you what the code is about
- Features are loosely coupled by default — dependencies between
  features are visible and intentional rather than implicit
- Onboarding is faster — a new developer can understand one feature
  in isolation without understanding the whole codebase
- Aligns with a future microservices extraction — each feature package
  is already a candidate for an independent service

**Negative:**
- Slightly more folders per feature compared to the flat layered approach
- `common/` requires discipline — it can become a dumping ground if
  classes are added without asking "is this truly cross-cutting?"
- The old layered structure is still the default in most tutorials and
  Spring initializr output — new developers may need to be oriented

## Alternatives Considered

- **Keep the layered structure**: Simple and familiar, but does not
  scale beyond a handful of controllers. Rejected as the project already
  has 17 services and will grow further.
- **Hexagonal (ports and adapters) architecture**: More principled
  separation of domain and infrastructure, but significantly more
  indirection and boilerplate for the current team size and scope.
  Can be revisited in the microservices phase.