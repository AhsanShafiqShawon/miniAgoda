# ADR-003: Concurrency Approach

## Status
Accepted

## Context

miniAgoda is explicitly designed to handle meaningful concurrent load — multiple users searching and booking simultaneously. Two key risks:

1. **Double booking**: Two threads check availability at the same time, both see a room as free, and both create a booking — exceeding `totalRooms`.
2. **Stale reads during search**: A search reads partial state while a booking is being written.

## Decision

Use a `ReentrantReadWriteLock` in `BookingRepository`:

- **Read lock** (shared): acquired for all search/count operations. Multiple threads can hold it simultaneously.
- **Write lock** (exclusive): acquired for booking creation. Only one thread can hold it; all reads block until it's released.

The check-then-book operation is wrapped in a single write-locked critical section to eliminate the TOCTOU (Time-Of-Check-Time-Of-Use) race:

```java
writeLock.lock();
try {
    int booked = countOverlapping(hotelId, roomTypeId, checkIn, checkOut);
    if (booked >= roomType.totalRooms()) throw new RoomNotAvailableException();
    bookings.add(new Booking(...));
} finally {
    writeLock.unlock();
}
```

All domain objects (`Booking`, `RatePolicy`, `AvailableRoomType`, `SearchResult`) are immutable Java records — they require no synchronization once created.

No static mutable state exists anywhere in the codebase.

## Consequences

**Positive:**
- Eliminates double-booking without complex distributed locking
- Read-heavy workloads (searches) are not blocked by each other
- Immutable records are safe to pass across threads freely
- Straightforward to reason about and test

**Negative:**
- Write lock is coarse-grained: one booking at a time across the entire repository. Acceptable for a monolith; will need room-type-level locking or optimistic concurrency in the microservices phase.
- Does not handle distributed concurrency — not relevant until services run in multiple JVMs.

## Future Evolution

In the microservices phase, the locking strategy will move to:
- Optimistic locking with database-level version columns, or
- A dedicated booking/inventory service with compare-and-swap semantics