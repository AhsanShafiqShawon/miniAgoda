# Feature Implementation Checklist

> A bottom-up guide for implementing every new feature in a Spring Boot + PostgreSQL project.
> Always build in this order. The controller is the last thing you write, not the first.

---

## The Golden Rule

**Build bottom-up, not top-down.**

The database is the foundation. Everything above it depends on it. If you start with the controller and work downward, you will constantly rewrite things as the shape of your data changes underneath you.

```
Controller        ← last
Service
Gateway interface + implementation   ← only if external service involved
Mapper
Exception
Repository
DTO
Entity            ← first
```

---

## Folder Structure

Every feature module follows the same layout:

```
feature/
├── FeatureController.java       HTTP layer — routes and request handling
├── FeatureService.java          Business logic
├── FeatureRepository.java       Database queries
├── FeatureMapper.java           Converts entity ↔ DTO
├── dto/
│   ├── FeatureRequest.java      What the API receives  (record)
│   └── FeatureResponse.java     What the API returns   (record)
├── entity/
│   ├── Feature.java             Database entity        (@Entity class, never a record)
│   └── FeatureStatus.java       Status enum            (lives here, not in common/)
├── value/
│   └── valueObjects.java
├── exception/
│   └── FeatureNotFoundException.java   Feature-specific exceptions
└── gateway/                     Only present if the feature calls an external service
    ├── FeatureGateway.java      Interface — the only thing the service imports
    └── provider/
        └── ProviderFeatureGateway.java  Concrete implementation (Stripe, S3, SendGrid…)
```

---

## Step 1 — Entity

**What does the database row look like?**

- Use a regular `class` annotated with `@Entity` — never a `record`
- Records are immutable; JPA requires a no-arg constructor and mutable fields
- Define enums (status, role, type) in `entity/` alongside the class that owns them
- Use `@GeneratedValue(strategy = GenerationType.UUID)` for IDs
- Always add `@CreationTimestamp` and `@UpdateTimestamp`

```java
@Entity
@Table(name = "features")
@Getter @Setter @NoArgsConstructor
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureStatus status = FeatureStatus.ACTIVE;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

**Checklist:**
- [ ] `@Entity` and `@Table(name = "...")` present
- [ ] `@Id` with UUID generation strategy
- [ ] All non-nullable columns marked `nullable = false`
- [ ] Enums use `@Enumerated(EnumType.STRING)` — never ORDINAL
- [ ] `createdAt` and `updatedAt` timestamps present
- [ ] No business logic inside the entity

---

## Step 2 — Repository

**How do I query the database?**

- Extend `JpaRepository<Entity, IdType>`
- Spring Data JPA generates basic CRUD for free
- Add custom queries as method names or `@Query` for complex cases

```java
public interface FeatureRepository extends JpaRepository<Feature, String> {

    Optional<Feature> findByName(String name);

    boolean existsByName(String name);

    List<Feature> findAllByStatus(FeatureStatus status);

    @Query("SELECT f FROM Feature f WHERE f.status = :status ORDER BY f.createdAt DESC")
    List<Feature> findActiveFeatures(@Param("status") FeatureStatus status);
}
```

**Checklist:**
- [ ] Extends `JpaRepository<Entity, IdType>`
- [ ] Returns `Optional<T>` for single-result queries — never return `null`
- [ ] `existsBy...` used for uniqueness checks (cheaper than `findBy...`)
- [ ] No business logic in the repository — queries only

---

## Step 3 — DTOs

**What does the API receive and return?**

- Use `record` for all DTOs — immutable, concise, no boilerplate
- Separate request DTOs from response DTOs — they are rarely the same shape
- Add validation annotations on request DTOs
- Never expose entity objects directly to the API layer

```java
// What the API receives
public record FeatureRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be under 100 characters")
    String name,

    @NotNull(message = "Status is required")
    FeatureStatus status
) {}

// What the API returns
public record FeatureResponse(
    String id,
    String name,
    String status,
    LocalDateTime createdAt
) {}
```

**Checklist:**
- [ ] All DTOs are `record` types
- [ ] Request DTOs have `@NotBlank`, `@NotNull`, `@Size` etc. where appropriate
- [ ] Response DTOs never include sensitive fields (passwords, internal flags)
- [ ] Request and response are separate classes — not the same object
- [ ] No `@Entity` annotations on DTOs

---

## Step 4 — Mapper

**How do I convert between entity and DTO?**

Without a mapper, conversion logic leaks into the service and grows messy as entities grow. The mapper keeps the service focused on business logic only.

```java
@Component
public class FeatureMapper {

    public FeatureResponse toResponse(Feature feature) {
        return new FeatureResponse(
            feature.getId(),
            feature.getName(),
            feature.getStatus().name(),
            feature.getCreatedAt()
        );
    }

    public List<FeatureResponse> toResponseList(List<Feature> features) {
        return features.stream()
            .map(this::toResponse)
            .toList();
    }

    public Feature toEntity(FeatureRequest request) {
        Feature feature = new Feature();
        feature.setName(request.name());
        feature.setStatus(request.status());
        return feature;
    }
}
```

**Checklist:**
- [ ] Annotated with `@Component` so Spring can inject it
- [ ] `toResponse(Entity)` — entity to response DTO
- [ ] `toResponseList(List<Entity>)` — bulk conversion
- [ ] `toEntity(Request)` — request DTO to entity (for create operations)
- [ ] No business logic in the mapper — conversion only
- [ ] No database calls in the mapper

---

## Step 5 — Exceptions

**What can go wrong specific to this feature?**

- Feature-specific exceptions live in `feature/exception/`
- They extend a base exception from `common/exception/`
- `GlobalExceptionHandler` in `common/` catches them all automatically
- Spring resolves the most specific handler first

```
common/exception/                          feature/exception/
─────────────────────────────              ──────────────────────────────────
NotFoundException        (404)    ←─────── FeatureNotFoundException
ConflictException        (409)    ←─────── FeatureAlreadyExistsException
ForbiddenException       (403)    ←─────── FeatureAccessDeniedException
UnauthorizedException    (401)
ValidationException      (400)
```

```java
// feature/exception/FeatureNotFoundException.java
public class FeatureNotFoundException extends NotFoundException {
    public FeatureNotFoundException(String id) {
        super("Feature not found: " + id);
    }
}
```

**Checklist:**
- [ ] Each exception extends the appropriate base class from `common/exception/`
- [ ] Constructor takes the relevant identifier as a parameter
- [ ] Message is human-readable and includes the identifier
- [ ] `GlobalExceptionHandler` already handles the base class — no extra handler needed unless you want a different HTTP response shape

---

## Step 6 — Gateway *(only if the feature calls an external service)*

**Does this feature talk to anything outside the application?**

A gateway is an interface your service depends on. The concrete implementation — Stripe,
S3, SendGrid, or a mock — is wired by Spring at runtime based on the active profile.
The service never imports the concrete class. Only the interface.

Three features in miniAgoda have a gateway:

| Feature | Gateway interface | Calls |
|---|---|---|
| `payment/` | `PaymentGateway` | Stripe / Omise |
| `image/` | `StorageGateway` | AWS S3 / GCS |
| `notification/` | `EmailGateway` | SendGrid / SES |

```java
// gateway/PaymentGateway.java — the interface the service sees
public interface PaymentGateway {
    PaymentGatewayResult charge(CreatePaymentRequest request);
    RefundGatewayResult refund(String gatewayTransactionId, BigDecimal amount, String currencyCode);
}

// gateway/stripe/StripePaymentGateway.java — production implementation
@Component
@Profile("production")
public class StripePaymentGateway implements PaymentGateway {
    @Override
    public PaymentGatewayResult charge(CreatePaymentRequest request) {
        // Stripe SDK call
    }
}

// gateway/mock/MockPaymentGateway.java — dev/test implementation
@Component
@Profile("dev")
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public PaymentGatewayResult charge(CreatePaymentRequest request) {
        // Returns hardcoded success — no real credentials needed locally
    }
}
```

The service constructor receives the interface, not the implementation:

```java
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository repository;
    private final PaymentGateway paymentGateway;   // interface only
}
```

**Checklist:**
- [ ] Interface lives at `feature/gateway/FeatureGateway.java`
- [ ] Production implementation in `feature/gateway/provider/`
- [ ] Mock/dev implementation in `feature/gateway/mock/`
- [ ] Each implementation annotated with `@Component` and `@Profile("...")`
- [ ] Service imports the interface — never the concrete class
- [ ] No business logic in the gateway — external call only
- [ ] Gateway throws a feature-specific exception on failure (e.g. `PaymentFailedException`)

---

## Step 7 — Service

**What is the business logic?**

- The service is the only place where business rules live
- It calls the repository for data, the mapper for conversion, and throws exceptions for failures
- Annotate with `@Service` and `@RequiredArgsConstructor`
- Use `@Transactional` on methods that write to the database

```java
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepository repository;
    private final FeatureMapper mapper;

    public FeatureResponse getById(String id) {
        Feature feature = repository.findById(id)
            .orElseThrow(() -> new FeatureNotFoundException(id));
        return mapper.toResponse(feature);
    }

    public List<FeatureResponse> getAll() {
        return mapper.toResponseList(repository.findAll());
    }

    @Transactional
    public FeatureResponse create(FeatureRequest request) {
        if (repository.existsByName(request.name())) {
            throw new FeatureAlreadyExistsException(request.name());
        }
        Feature feature = mapper.toEntity(request);
        return mapper.toResponse(repository.save(feature));
    }

    @Transactional
    public FeatureResponse update(String id, FeatureRequest request) {
        Feature feature = repository.findById(id)
            .orElseThrow(() -> new FeatureNotFoundException(id));
        feature.setName(request.name());
        feature.setStatus(request.status());
        return mapper.toResponse(repository.save(feature));
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new FeatureNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
```

**Checklist:**
- [ ] Annotated with `@Service` and `@RequiredArgsConstructor`
- [ ] All write methods annotated with `@Transactional`
- [ ] Throws feature-specific exceptions — never returns `null`
- [ ] Uses mapper for all entity ↔ DTO conversions
- [ ] No HTTP concepts here — no `HttpServletRequest`, no `ResponseEntity`
- [ ] No raw SQL — delegate all queries to the repository

---

## Step 8 — Controller

**What are the HTTP endpoints?**

- The controller is the thinnest layer — it only handles HTTP in and out
- It calls the service and wraps the result in `ApiResponse<T>`
- No business logic here — if you find yourself writing an `if` statement, it belongs in the service
- Annotate with `@RestController`, `@RequestMapping`, and `@RequiredArgsConstructor`

```java
@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FeatureResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(service.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FeatureResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FeatureResponse>> create(
        @Valid @RequestBody FeatureRequest request) {
        return ResponseEntity.status(201)
            .body(ApiResponse.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FeatureResponse>> update(
        @PathVariable String id,
        @Valid @RequestBody FeatureRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Checklist:**
- [ ] Annotated with `@RestController`, `@RequestMapping("/api/v1/...")`, `@RequiredArgsConstructor`
- [ ] `@Valid` on every `@RequestBody` — triggers DTO validation
- [ ] Returns `ResponseEntity<ApiResponse<T>>` for data, `ResponseEntity<Void>` for deletes
- [ ] `POST` returns `201 Created`, `DELETE` returns `204 No Content`, everything else `200 OK`
- [ ] No business logic — one line per endpoint, delegating to the service
- [ ] No direct repository calls — always goes through the service

---

## The Dependency Rule

Module dependencies should only ever point in one direction — never in a circle.

```
Controller  →  Service  →  Repository  →  Entity
                        →  Gateway (interface)
                        →  Mapper
                        →  Exception
```

The service never reaches past the gateway interface. The concrete implementation
(Stripe, S3, SendGrid) is invisible to the service — Spring injects it at startup.

---

## Quick Reference — Types at a Glance

| File | Java type | Annotation |
|------|-----------|------------|
| `Feature.java` (entity) | `class` | `@Entity` |
| `FeatureStatus.java` | `enum` | none |
| `FeatureRequest.java` | `record` | none |
| `FeatureResponse.java` | `record` | none |
| `FeatureRepository.java` | `interface` | none (extends `JpaRepository`) |
| `FeatureGateway.java` | `interface` | none (only if external service used) |
| `ProviderFeatureGateway.java` | `class` | `@Component` + `@Profile` |
| `FeatureMapper.java` | `class` | `@Component` |
| `FeatureService.java` | `class` | `@Service` |
| `FeatureController.java` | `class` | `@RestController` |
| `FeatureNotFoundException.java` | `class` | none (extends base exception) |

---

## Full Checklist — One Page

```
Step 1 — Entity
  [ ] @Entity class (never a record)
  [ ] UUID primary key
  [ ] Nullable constraints
  [ ] Enums with @Enumerated(EnumType.STRING)
  [ ] createdAt + updatedAt timestamps
  [ ] No business logic

Step 2 — Repository
  [ ] Extends JpaRepository<Entity, String>
  [ ] Optional<T> for single results
  [ ] existsBy... for uniqueness checks
  [ ] No business logic

Step 3 — DTOs
  [ ] Records for all DTOs
  [ ] Validation annotations on requests
  [ ] Separate request and response types
  [ ] No sensitive fields in responses

Step 4 — Mapper
  [ ] @Component
  [ ] toResponse(Entity)
  [ ] toResponseList(List<Entity>)
  [ ] toEntity(Request)
  [ ] No business logic, no DB calls

Step 5 — Exceptions
  [ ] Extends appropriate base from common/exception/
  [ ] Constructor takes identifier as parameter
  [ ] Human-readable message

Step 6 — Gateway  (skip if no external service)
  [ ] Interface at feature/gateway/FeatureGateway.java
  [ ] Production implementation with @Profile("production")
  [ ] Mock implementation with @Profile("dev")
  [ ] Service imports interface only — never the concrete class
  [ ] No business logic in gateway — external call only
  [ ] Throws feature-specific exception on failure

Step 7 — Service
  [ ] @Service + @RequiredArgsConstructor
  [ ] @Transactional on all write methods
  [ ] Throws exceptions, never returns null
  [ ] Uses mapper for conversions
  [ ] No HTTP concepts

Step 8 — Controller
  [ ] @RestController + @RequestMapping + @RequiredArgsConstructor
  [ ] @Valid on all @RequestBody params
  [ ] Correct HTTP status codes (201, 204, 200)
  [ ] No business logic
  [ ] No direct repository calls
```