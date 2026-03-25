# API Contract: Hotel Management

## Overview

`HotelManagementService` is responsible for hotel operational queries —
bookings by guest, availability tracking, revenue reporting, and occupancy
rates. It is NOT responsible for hotel data management (that's `HotelService`),
guest-facing booking operations (that's `BookingService`), or room type
management (that's `RoomTypeService`).

## Collaborators

```java
@Service
public class HotelManagementService {
    private final BookingRepository bookingRepository;
    private final InventoryRepository inventoryRepository;
    private final HotelRepository hotelRepository;
}
```

---

## Methods

### `getAllBookingsByGuest(UUID userId, UUID hotelId, int page, int size)`

Returns all bookings made by a specific guest at a specific hotel, paginated.

```java
List<BookingSummary> getAllBookingsByGuest(
    UUID userId, UUID hotelId, int page, int size);
```

---

### `getAvailability(UUID hotelId, int page, int size)`

Returns availability status for all room types in a hotel, paginated.

```java
List<RoomTypeAvailability> getAvailability(UUID hotelId, int page, int size);
```

**Behavior:**
1. Fetch all room types for the hotel
2. For each room type, count current bookings via `InventoryRepository`
3. Derive `AvailabilityStatus`:
   - `bookedRooms == 0` → `AVAILABLE`
   - `bookedRooms < totalRooms` → `PARTIALLY_BOOKED`
   - `bookedRooms == totalRooms` → `FULLY_BOOKED`
4. Return `RoomTypeAvailability` records

---

### `getAvailabilityByRoomType(UUID roomTypeId, int page, int size)`

Returns availability for a specific room type, paginated.

```java
List<RoomTypeAvailability> getAvailabilityByRoomType(
    UUID roomTypeId, int page, int size);
```

---

### `getAvailabilityByStatus(UUID hotelId, AvailabilityStatus status, int page, int size)`

Returns room types filtered by availability status, paginated.

```java
List<RoomTypeAvailability> getAvailabilityByStatus(
    UUID hotelId, AvailabilityStatus status, int page, int size);
```

---

### `getRevenue(UUID hotelId, RevenuePeriod period)`

Returns revenue for a hotel for the given period. Revenue is broken down
per currency — not converted to a single base currency.

```java
Revenue getRevenue(UUID hotelId, RevenuePeriod period);
```

**Period ranges:**
- `DAY` — today (midnight to now)
- `WEEK` — last 7 days
- `MONTH` — current calendar month
- `YEAR` — current calendar year

**Behavior:**
1. Fetch all `CONFIRMED` and `COMPLETED` bookings for the hotel in the period
2. Group `totalPrice` by `currencyCode`
3. Return `Revenue` with `revenuePerCurrency` map

---

### `getOccupancyRate(UUID hotelId, LocalDate date)`

Returns the occupancy rate for a hotel on a specific date.

```java
OccupancyRate getOccupancyRate(UUID hotelId, LocalDate date);
```

**Behavior:**
1. Fetch all room types for the hotel — sum `totalRooms`
2. Count bookings active on `date` via `InventoryRepository`
3. `rate` = `occupiedRooms` / `totalRooms`
4. Return `OccupancyRate`

---

## Error Cases

| Condition | Exception |
|---|---|
| Hotel not found | `ResourceNotFoundException` |
| Room type not found | `ResourceNotFoundException` |
| User not found | `ResourceNotFoundException` |
| `date` is null | `InvalidRequestException` |