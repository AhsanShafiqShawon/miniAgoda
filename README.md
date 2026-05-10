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
│   │   ├── search/
│   │   │   ├── controller/
│   │   │   │   └── SearchController.java
│   │   │   ├── service/
│   │   │   │   └── SearchService.java
│   │   │   └── dto/
│   │   │       ├── SearchRequest.java
│   │   │       └── SearchResponse.java
│   │   └── MiniAgodaApplication.java
│   │
│   ├── test/java/com/miniagoda/
│   │   └──
│   │
│   └── main/resources/
│       ├── application.yml
│       └── db/migration/
│           └──
├── .env
├── pom.xml
└── README.md
```