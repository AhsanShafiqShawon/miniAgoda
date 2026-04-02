# Why You Shouldn't Use Records for JPA Entities

## What Is a Record?

Introduced in Java 16, a record is a special kind of class designed for
one purpose: **carrying immutable data concisely**.

```java
public record UserProfileResponse(String id, String email, String role) {}
```

That single line gives you:
- Three `private final` fields
- An all-args constructor
- Getters for each field (`id()`, `email()`, `role()`)
- `equals()`, `hashCode()`, and `toString()` — all generated automatically

No boilerplate. No setters. Fields are final — they cannot change after
the object is created.

---

## What Is a JPA Entity?

A JPA entity is a regular Java class that maps to a database table. JPA
(Java Persistence API) — implemented by Hibernate in Spring Boot — is
responsible for reading rows from the database and turning them into
Java objects, and for watching those objects for changes and writing
them back.

```java
@Entity
@Table(name = "users")
public class User {

    @Id
    private String id;
    private String email;
    private String password;
    private String role;
    private String status;

    public User() {}   // no-args constructor — required by JPA

    // getters and setters
}
```

JPA has very specific requirements about how an entity class must be
structured. Records violate all of them.

---

## Constructors — The Foundation

Before understanding why records fail, you need to understand the two
kinds of constructors involved.

### No-Args Constructor

A constructor that takes **no parameters**. Creates an empty object with
all fields at their default values (`null`, `0`, `false`).

```java
public class User {
    private String id;
    private String email;

    public User() {
        // no parameters — fields start as null
    }
}

User user = new User();   // ✅ works — id and email are null
```

**Why it exists:** Some frameworks and libraries need to create an object
before they know what values to put in it. They create the empty shell
first, then fill in the fields one at a time. JPA is the most important
example — it reads columns from a database result set one by one and
sets each field individually. It needs to create the object before it
has read all the columns.

If a class has no no-args constructor, these frameworks cannot
instantiate it. Java provides a default no-args constructor automatically
— but only if you haven't defined any other constructor. The moment you
define any constructor yourself, Java removes the default.

### All-Args Constructor

A constructor that takes **every field as a parameter**. Creates a fully
populated object in one call — every field must be supplied upfront.

```java
public class User {
    private String id;
    private String email;

    public User(String id, String email) {
        this.id    = id;
        this.email = email;
    }
}

User user = new User("uuid-123", "shawon@email.com");   // ✅ works
User user = new User();   // ❌ does not exist — compiler error
```

**Why it exists:** When you know all values at creation time and want
to enforce that no field is ever null or in an uninitialised state.
Immutable objects — ones where fields are `final` — require all-args
constructors because there is no way to set fields after construction.

### What Records Give You

A record automatically generates an all-args constructor only:

```java
public record User(String id, String email) {}

// what the compiler generates:
public User(String id, String email) {   // all-args — auto-generated
    this.id    = id;
    this.email = email;
}

// what is NOT generated:
public User() {}   // ❌ no-args — does not exist
```

The fields are `final`. There are no setters. Once constructed, nothing
can change.

---

## Why Records and JPA Fight Each Other

### Problem 1 — JPA Requires a No-Args Constructor. Records Don't Have One.

When JPA reads a row from the database, it does not have all column
values available at once. It creates the object first, then populates
fields as it reads each column:

```java
// What JPA does internally:
User user = new User();              // step 1 — create empty object
user.setId(rs.getString("id"));      // step 2 — fill id
user.setEmail(rs.getString("email")); // step 3 — fill email
user.setRole(rs.getString("role"));  // step 4 — fill role
// ... one field at a time
```

A record only has an all-args constructor. JPA cannot call `new User()`
— it doesn't exist. Hibernate throws:

```
org.hibernate.InstantiationException:
No default constructor for entity: com.miniagoda.user.entity.User
```

The application fails to start or crashes the moment JPA tries to load
a user from the database.

---

### Problem 2 — Records Are Immutable. JPA Requires Mutability.

JPA uses a mechanism called **dirty checking**. After loading an entity,
JPA takes a snapshot of its field values. At the end of the transaction,
it compares the current state to the snapshot. If anything changed, it
automatically generates an `UPDATE` SQL statement.

```java
// JPA dirty checking in action:
User user = userRepository.findById(id);
// JPA snapshots: { id: "uuid", status: "ACTIVE", failedLoginAttempts: 2 }

user.setStatus(UserStatus.BANNED);
user.setFailedLoginAttempts(0);
// JPA detects changes at end of transaction →
// UPDATE users SET status='BANNED', failed_login_attempts=0 WHERE id='uuid'
```

Record fields are `final`. You cannot call `user.setStatus(...)` — there
are no setters and fields cannot be reassigned. Dirty checking has
nothing to watch. Every update operation becomes impossible without
replacing the entire object — which defeats the entire purpose of an ORM.

---

### Problem 3 — Records Cannot Be Subclassed. JPA Requires Proxy Generation.

JPA uses **lazy loading** to avoid fetching related data from the
database until it is actually needed. It achieves this by creating a
**proxy subclass** of your entity at runtime — a subclass that intercepts
field access and triggers a database query on demand.

```java
// JPA creates something like this at runtime for lazy loading:
class UserProxy extends User {
    private boolean loaded = false;

    @Override
    public String getEmail() {
        if (!loaded) {
            fetchFromDatabase();   // only hits DB when you actually access the field
            loaded = true;
        }
        return super.getEmail();
    }
}
```

Records are implicitly `final` — they cannot be subclassed. JPA cannot
create a proxy. Lazy loading breaks entirely. Hibernate either throws an
error or silently falls back to eager loading — fetching everything from
the database whether you need it or not, killing performance on large
datasets.

---

### Problem 4 — Records Have No Setters. JPA Cannot Populate Fields.

Even if JPA could construct the object (it can't), it has no way to
populate the fields after construction. Records only generate getters —
`user.email()`, not `user.setEmail("...")`. JPA's reflection-based field
population has nowhere to write.

---

## The Four Requirements — Summary

| JPA Requirement | Why JPA Needs It | Record | Regular Class |
|---|---|---|---|
| No-args constructor | Creates empty object before reading columns | ❌ Only all-args | ✅ |
| Mutable fields | Dirty checking detects changes and generates UPDATE | ❌ All fields final | ✅ |
| Setters | Populates fields after construction | ❌ No setters | ✅ |
| Subclassable | Proxy generation for lazy loading | ❌ Implicitly final | ✅ |

Records fail all four. They are the wrong tool for entities.

---

## Where Records Do Belong — DTOs

Records are not useless — they are in the wrong place when used for
entities. They are actually **perfect for DTOs**.

DTOs are created once, passed through layers, and never mutated. That
is exactly what a record is designed for — an immutable data carrier.
Concise, no boilerplate, no risk of a field being accidentally changed
mid-flow.

```java
// ✅ Request DTOs — records are ideal
public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank        String password
) {}

public record RegisterRequest(
    @Email @NotBlank       String email,
    @Size(min = 8)         String password,
    @NotBlank              String fullName
) {}

// ✅ Response DTOs — records are ideal
public record TokenResponse(
    String accessToken,
    String refreshToken
) {}

public record UserProfileResponse(
    String id,
    String email,
    String role
) {}

public record HotelSummaryResponse(
    String hotelId,
    String name,
    double rating,
    double startingFromPrice
) {}
```

---

## The Clean Rule

The architecture boundary maps directly onto the Java type to use:

```
Entity  → regular class    mutable, JPA-managed, lives at the DB boundary
DTO     → record           immutable, plain carrier, lives at the HTTP boundary
```

```
┌──────────────────────────────────────────────────┐
│  CLIENT                                          │
└──────────────────────┬───────────────────────────┘
                       │
              ┌────────▼────────┐
              │   record ✅     │  ← Request DTO (immutable inbound carrier)
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │   CONTROLLER    │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │    SERVICE      │  ← converts record ↔ class here
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │   class ✅      │  ← Entity (mutable, JPA-managed)
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │   REPOSITORY    │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │   DATABASE      │
              └─────────────────┘
                       │
              ┌────────▼────────┐
              │   class ✅      │  ← Entity returned from DB
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │    SERVICE      │  ← converts class → record here
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │   record ✅     │  ← Response DTO (immutable outbound carrier)
              └────────┬────────┘
                       │
┌──────────────────────▼───────────────────────────┐
│  CLIENT                                          │
└──────────────────────────────────────────────────┘
```

---

## Glossary

| Term | Meaning |
|---|---|
| Record | A Java class designed for immutable data — final fields, all-args constructor, auto-generated getters, equals, hashCode |
| No-args constructor | A constructor with no parameters — creates an empty object for frameworks to populate |
| All-args constructor | A constructor requiring every field — creates a fully populated object in one call |
| JPA | Java Persistence API — maps Java objects to database rows |
| Hibernate | The JPA implementation Spring Boot uses under the hood |
| Dirty checking | JPA's mechanism for detecting field changes and generating UPDATE statements automatically |
| Lazy loading | Fetching related data only when accessed, not upfront |
| Proxy | A JPA-generated subclass that intercepts field access to enable lazy loading |
| `final` field | A field that cannot be reassigned after the object is constructed |
| Immutable | An object whose state cannot change after construction — all fields final, no setters |

---

## One-Paragraph Summary

Records cannot be used for JPA entities because they violate all four
requirements JPA depends on. JPA needs a no-args constructor to create
an empty object before populating fields one by one from a result set —
records only have an all-args constructor. JPA needs mutable fields for
dirty checking, which detects changes and generates UPDATE statements
automatically — record fields are final and cannot change. JPA needs
setters to populate fields after construction — records have no setters.
JPA needs subclassable classes to generate proxies for lazy loading —
records are implicitly final. Records are however perfect for DTOs,
which are created once, passed through layers, and never mutated —
exactly what an immutable data carrier is designed for. The clean rule
is: entities use regular classes, DTOs use records.