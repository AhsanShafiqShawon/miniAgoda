# miniAgoda

A Java/Spring Boot hotel booking system modeled after Agoda, built with an explicit goal of evolving into a distributed microservices architecture.

## What it does

miniAgoda allows users to search for available hotels by city, date range,
guest count, and room preferences, make bookings, write reviews, and manage
their account. Hotel owners can manage properties, room types, pricing, and
view operational data. Admins can moderate content and manage the platform.

## Project Status

| Feature | Status | Covers |
|---|---|---|
| Search | ✅ Done | Hotel discovery, room types, availability |
| Booking | ⌛ In Progress | Reservation creation, inventory management |
| Payment | ⬜ Planned | Payment gateway, transaction handling |
| Notification | ⬜ Planned | Booking confirmations, refund alerts |
| User | ⬜ Planned | Registration, profile management |
| Auth | ⬜ Planned | Authentication, role-based authorization |
| Refund | ⬜ Planned | Refund requests, gateway integration |

## Architecture

miniAgoda is a modular monolith — all features run in a single JVM, but each feature module is self-contained and mirrors how the system would be split into microservices. The design is intentionally microservices-ready from day one.

```
Client
  └── Spring Boot Application
        ├── Feature Modules         (hotel/, booking/, user/, review/, ...)
        │     ├── controller/       (HTTP layer — routes requests in, sends responses out)
        │     ├── service/          (business logic)
        │     ├── repository/       (data access)
        │     ├── mapper/           (entity ↔ DTO conversion)
        │     ├── dto/              (request & response records)
        │     ├── entity/           (JPA entities & enums)
        │     ├── value/            (value objects)
        │     ├── exception/        (feature-scoped exceptions)
        │     └── gateway/          (external service abstractions, if needed)
        └── Common                  (shared config, exceptions, responses, utils, fitler, security)
```

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

## Implementation Progress

```
miniAgoda/
├── src/
│   ├── main/java/com/miniagoda/
│   │   ├── common/
│   │   │   ├── seed/
│   │   │   │   └── DataSeeder.java
│   │   │   └── entity/
│   │   │       └── BaseEntity.java
│   │   ├── search/
│   │   │   ├── controller/
│   │   │   │   └── SearchController.java
│   │   │   ├── service/
│   │   │   │   └── SearchService.java
│   │   │   └── dto/
│   │   │       ├── SearchRequest.java
│   │   │       └── SearchResponse.java
│   │   ├── hotel/
│   │   │   ├── controller/
│   │   │   │   └── HotelController.java
│   │   │   ├── repository/
│   │   │   │   ├── HotelRepository.java
│   │   │   │   └── RoomTypeRepository.java
│   │   │   ├── service/
│   │   │   │   └── HotelService.java
│   │   │   ├── entity/
│   │   │   │   ├── Hotel.java
│   │   │   │   └── RoomType.java
│   │   │   └── dto/
│   │   │       ├── RoomTypeSeed.java
│   │   │       ├── HotelDetailRequest.java
│   │   │       ├── HotelDetailResponse.java
│   │   │       └── RoomTypeResponse.java
│   │   ├── inventory/
│   │   │   ├── repository/
│   │   │   │   └── InventoryRepository.java
│   │   │   ├── service/
│   │   │   │   └── InventoryService.java
│   │   │   └── entity/
│   │   │       └── Inventory.java
│   │   ├── booking/
│   │   │   ├── controller/
│   │   │   │   └── BookingController.java
│   │   │   ├── service/
│   │   │   │   └── BookingService.java
│   │   │   ├── repository/
│   │   │   │   └── BookingRepository.java
│   │   │   ├── entity/
│   │   │   │   ├── Booking.java
│   │   │   │   └── BookingStatus.java
│   │   │   └── dto/
│   │   │       ├── BookingResponse.java
│   │   │       └── BookingRequest.java
│   │   ├── payment/
│   │   │   ├── controller/
│   │   │   │   └── PaymentWebhookController.java
│   │   │   ├── repository/
│   │   │   │   └── PaymentRepository.java
│   │   │   ├── service/
│   │   │   │   ├── PaymentService.java
│   │   │   │   ├── PaymentWebhookService.java
│   │   │   │   └── StripeService.java
│   │   │   ├── entity/
│   │   │   │   ├── Payment.java
│   │   │   │   └── PaymentStatus.java
│   │   │   └── dto/
│   │   │       ├── PaymentInitiateResponse.java
│   │   │       └── PaymentInitiateRequest.java
│   │   └── MiniAgodaApplication.java
│   │
│   ├── test/java/com/miniagoda/
│   │   └── 
│   │
│   └── main/resources/
│       ├── application.yml
│       ├── data/
│       │   └── room_types.json
│       └── db/migration/
│           ├── V1__init_schema.sql
│           ├── V2__add_rating_and_hotel_code_to_hotels.sql
│           ├── V3__seed_bangkok_hotels.sql
│           ├── V4__alter_room_type_rename_total_rooms_to_capacity_add_total_units.sql
│           ├── V5__alter_hotel_rename_hotel_code_to_code.sql
│           ├── V6__update_inventory_table.sql
│           ├── V7__add_city_to_hotels.sql
│           ├── V8__create_bookings_table.sql
│           ├── V9__add_expired_at_to_bookings.sql
│           └── V10__create_payments_table
├── .env
├── pom.xml
└── README.md
```