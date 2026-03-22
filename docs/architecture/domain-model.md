# Domain Model

## Entities

### `Country`

Represents a country that cities belong to. Holds standardized codes for
internationalization and display purposes.

```java
public record Country(
    UUID id,
    String name,
    String isoCode,        // ISO 3166-1 alpha-2, e.g. "BD", "TH", "SG"
    String phoneCode,      // e.g. "+880", "+66"
    String currencyCode    // ISO 4217, e.g. "BDT", "THB", "USD"
) {}
```

**Validation rules (service layer):**
- `isoCode` — exactly 2 uppercase characters
- `currencyCode` — exactly 3 uppercase characters
- `phoneCode` — starts with `+` followed by digits

---

### `City`

Represents a destination that hotels belong to and the target of a
city-based search. References `Country` by ID only — no embedded object.

```java
public record City(
    UUID id,
    String name,
    String timezone,            // IANA timezone, e.g. "Asia/Dhaka"
    Coordinates coordinates,
    UUID countryId
) {}
```

---

## Value Objects

---

### `Coordinates`
A geographic coordinate pair. Used by `City` and potentially by
`RecommendationService` for proximity-based suggestions.

```java
public record Coordinates(
    double latitude,
    double longitude
) {}
```

---