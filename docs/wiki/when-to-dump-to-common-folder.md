# The `common/` Directory Gate Test

> Before adding **anything** to `common/`, you must pass three questions. All three must be **yes**. If any answer is no, it belongs in a specific module.

---

## The Three Questions

```
1. Is it used by 3 or more modules?
2. Does it have no natural "home" in any single module?
3. Is it genuinely generic — not tied to any domain concept?
```

---

## Why This Test Exists

`common/` (or `shared/`, `util/`, `core/` — whatever your project calls it) is one of the most abused directories in software. It becomes a dumping ground for anything that "doesn't feel like it belongs" in the module you're currently working in. The result is a bloated, tangled package with unclear ownership and invisible coupling.

The three-question test forces you to earn the right to use `common/`.

---

## Breaking Down Each Question

### 1. Is it used by 3 or more modules?

One or two callers doesn't justify promotion to shared territory. If only `HotelSearchService` and `HotelRepository` use a utility, keep it closer to that domain. Three or more independent consumers is the first signal that something has earned a general-purpose address.

> **Watch out for:** pre-emptive abstraction — moving something to `common/` because you *imagine* it will be reused. Wait until reuse actually happens.

---

### 2. Does it have no natural "home" in any single module?

If you hesitate for less than two seconds and can name a module it fits in — it fits there. `common/` is for the genuinely homeless, not the merely inconvenient.

A `DateFormatter` with no domain knowledge? Homeless. A `CheckInDateFormatter` that knows about booking windows and grace periods? It lives in the booking module, full stop.

> **Watch out for:** moving things to `common/` to avoid a naming decision. Difficulty naming where something belongs is a symptom that it needs to be redesigned, not relocated.

---

### 3. Is it genuinely generic — not tied to any domain concept?

This is the hardest question to answer honestly. Domain concepts have a way of leaking into utility code invisibly — through parameter names, through the shape of the data they accept, through the assumptions baked into their logic.

Ask yourself: *could this code be dropped unchanged into a completely unrelated project?* If the answer involves "well, as long as they also have a `Hotel` class…" — that's a no.

> **Watch out for:** utilities that take domain objects as parameters. `formatPrice(hotel.price)` is not generic. `formatCurrency(amount, locale)` is.

---

## Decision Matrix

| Q1 (3+ modules) | Q2 (no home) | Q3 (generic) | Verdict |
|:-:|:-:|:-:|---|
| ✅ | ✅ | ✅ | ✅ Safe to put in `common/` |
| ✅ | ✅ | ❌ | ❌ Belongs in the domain module that "owns" the concept |
| ✅ | ❌ | ✅ | ❌ Belongs in the module with the natural home |
| ❌ | ✅ | ✅ | ❌ Too early — leave it where the first consumer is |
| Any other combo | | | ❌ Keep it in a specific module |

---

## Practical Examples

| Candidate | Q1 | Q2 | Q3 | Decision |
|---|:-:|:-:|:-:|---|
| `StringUtils.capitalize()` | ✅ | ✅ | ✅ | `common/` |
| `PaginationHelper` (generic offset/limit) | ✅ | ✅ | ✅ | `common/` |
| `HotelPriceFormatter` | ✅ | ❌ | ❌ | `hotel/` module |
| `DateRangeValidator` (used only in search) | ❌ | — | — | `search/` module |
| `BookingStatusMapper` | ✅ | ❌ | ❌ | `booking/` module |

---

## The Underlying Principle

**Common is not a default. It's a promotion.**

Code earns its place in `common/` by demonstrating that it is genuinely cross-cutting, domain-free, and already needed in multiple places. Everything else starts life in the module where it's first needed and *stays there* until proven otherwise.

A lean `common/` is a healthy `common/`.