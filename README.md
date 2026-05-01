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
│   │   │   ├── entity/
│   │   │   │   └── [x] BaseEntity.java
│   │   │   └── seed/
│   │   │       ├── [x] DataSeeder.java
│   │   │       └── [x] RoomTypeSeed.java
│   │   ├── hotel/
│   │   │   ├── controller/
│   │   │   │   └── [ ] HotelController.java
│   │   │   ├── repository/
│   │   │   │   ├── [x] HotelRepository.java
│   │   │   │   └── [x] RoomTypeRepository.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] HotelSummary.java               ← record
│   │   │   │   ├── [ ] AddHotelRequest.java            ← record
│   │   │   │   ├── [ ] EditHotelRequest.java           ← record
│   │   │   │   ├── [ ] AddRoomTypeRequest.java         ← record
│   │   │   │   ├── [ ] EditRoomTypeRequest.java        ← record
│   │   │   │   ├── [ ] AddRatePolicyRequest.java       ← record
│   │   │   │   └── [ ] EditRatePolicyRequest.java      ← record
│   │   │   ├── entity/
│   │   │   │   ├── [x] Hotel.java                      ← @Entity class
│   │   │   │   ├── [ ] HotelStatus.java                ← enum
│   │   │   │   ├── [x] RoomType.java                   ← @Entity class
│   │   │   │   ├── [ ] RoomTypeStatus.java             ← enum
│   │   │   │   ├── [ ] RoomCategory.java               ← enum
│   │   │   │   ├── [ ] BedType.java                    ← enum
│   │   │   │   └── [ ] Amenity.java                    ← enum
│   │   │   ├── value/
│   │   │   │   ├── [ ] Address.java                    ← value object
│   │   │   │   ├── [ ] Coordinates.java                ← value object
│   │   │   │   ├── [ ] RatePolicy.java                 ← value object
│   │   │   │   └── [ ] DiscountPolicy.java             ← value object
│   │   │   └── exception/
│   │   │       ├── [ ] HotelNotFoundException.java
│   │   │       └── [ ] RoomTypeNotFoundException.java
│   │   └── MiniAgodaApplication.java
│   │
│   ├── test/java/com/miniagoda/
│   │   └── [ ] hotel/
│   │
│   └── main/resources/
│       ├── [x] application.yml
│       ├── data/
│       │   ├── [x] hotels.json
│       │   └── [x] room_types.json
│       └── db/migration/
│           ├── [ ] V1__create_countries.sql
│           ├── [ ] V2__create_cities.sql
│           ├── [ ] V3__create_destinations.sql
│           ├── [ ] V4__create_users.sql
│           ├── [ ] V5__create_refresh_tokens.sql
│           ├── [ ] V6__create_images.sql
│           ├── [ ] V7__create_hotels.sql
│           ├── [ ] V8__create_room_types.sql
│           ├── [ ] V9__create_availability_blocks.sql
│           ├── [ ] V10__create_search_history.sql
│           ├── [ ] V11__create_payments.sql
│           ├── [ ] V12__create_refunds.sql
│           ├── [ ] V13__create_bookings.sql
│           ├── [ ] V14__create_notifications.sql
│           ├── [ ] V15__create_promotions.sql
│           └── [ ] V16__create_reviews.sql
│
├── setup/                                              ← everything needed to SET UP the app
│   ├── [ ] getting-started.md                          ← First file any developer should read
│   ├── [ ] environment-variables.md                    ← All .env variables explained
│   ├── [ ] database.md                                 ← DB setup, migrations, seeding
│   └── [ ] configuration.md                            ← application.properties / YAML explained
├── .env
├── .env.example
├── pom.xml
└── README.md
```