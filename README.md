# miniAgoda

A Java/Spring Boot hotel booking system modeled after Agoda, built with an explicit goal of evolving into a distributed microservices architecture.

## What it does

miniAgoda allows users to search for available hotels by city, date range,
guest count, and room preferences, make bookings, write reviews, and manage
their account. Hotel owners can manage properties, room types, pricing, and
view operational data. Admins can moderate content and manage the platform.

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
│   │   │   ├── repository/
│   │   │   │   ├── HotelRepository.java
│   │   │   │   └── RoomTypeRepository.java
│   │   │   ├── entity/
│   │   │   │   ├── Hotel.java
│   │   │   │   └── RoomType.java
│   │   │   └── dto/
│   │   │       └── RoomTypeSeed.java
│   │   ├── inventory/
│   │   │   ├── repository/
│   │   │   │   └── InventoryRepository.java
│   │   │   └── entity/
│   │   │       └── Inventory.java
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
│           └── V6__update_inventory_table.sql
├── .env
├── pom.xml
└── README.md
```