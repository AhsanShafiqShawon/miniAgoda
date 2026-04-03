# miniAgoda — Implementation Progress

## How to use
- `[ ]` not started
- `[x]` implemented

---

```
miniAgoda/
├── src/
│   ├── main/java/com/miniagoda/
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 1 — Foundation & Auth     ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── common/                            # Build this first. Everything depends on it.
│   │   │   ├── config/
│   │   │   │   ├── [ ] AppConfig.java
│   │   │   │   ├── [ ] SecurityConfig.java
│   │   │   │   └── [ ] JwtConfig.java
│   │   │   ├── exception/
│   │   │   │   ├── [ ] GlobalExceptionHandler.java
│   │   │   │   ├── [ ] NotFoundException.java
│   │   │   │   ├── [ ] ConflictException.java
│   │   │   │   ├── [ ] UnauthorizedException.java
│   │   │   │   ├── [ ] ForbiddenException.java
│   │   │   │   └── [ ] ValidationException.java
│   │   │   ├── response/
│   │   │   │   ├── [ ] ApiResponse.java
│   │   │   │   └── [ ] ErrorResponse.java
│   │   │   └── util/
│   │   │       └── [ ] JwtUtil.java
│   │   │
│   │   ├── user/                              # Build before auth. Auth depends on User.
│   │   │   ├── [ ] UserController.java
│   │   │   ├── [ ] UserService.java
│   │   │   ├── [ ] UserRepository.java
│   │   │   ├── [ ] UserMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] UserProfileResponse.java   ← record
│   │   │   │   └── [ ] UpdateProfileRequest.java  ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] User.java                  ← @Entity class
│   │   │   │   ├── [ ] UserRole.java               ← enum: GUEST, HOST, ADMIN
│   │   │   │   └── [ ] UserStatus.java             ← enum: ACTIVE, SUSPENDED, UNVERIFIED
│   │   │   └── exception/
│   │   │       └── [ ] UserNotFoundException.java
│   │   │
│   │   ├── auth/                              # Build after user. Depends on User entity.
│   │   │   ├── [ ] AuthController.java
│   │   │   ├── [ ] AuthService.java
│   │   │   ├── [ ] JwtAuthFilter.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] LoginRequest.java          ← record
│   │   │   │   ├── [ ] RegisterRequest.java       ← record
│   │   │   │   ├── [ ] TokenResponse.java         ← record
│   │   │   │   └── [ ] RefreshTokenRequest.java   ← record
│   │   │   ├── entity/
│   │   │   │   └── [ ] RefreshToken.java          ← @Entity class
│   │   │   └── exception/
│   │   │       ├── [ ] InvalidTokenException.java
│   │   │       └── [ ] TokenExpiredException.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 2 — Core Hotel Data       ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── destination/                       # Build before hotel. Hotels reference cities.
│   │   │   ├── [ ] DestinationService.java
│   │   │   ├── [ ] DestinationRepository.java
│   │   │   ├── [ ] DestinationMapper.java
│   │   │   ├── dto/
│   │   │   │   └── [ ] DestinationResponse.java   ← record
│   │   │   └── entity/
│   │   │       └── [ ] City.java                  ← @Entity class
│   │   │
│   │   ├── image/                             # Build before hotel. Hotels reference images.
│   │   │   ├── [ ] ImageController.java
│   │   │   ├── [ ] ImageService.java
│   │   │   ├── [ ] ImageRepository.java
│   │   │   ├── dto/
│   │   │   │   └── [ ] ImageUploadResponse.java   ← record
│   │   │   └── entity/
│   │   │       └── [ ] HotelImage.java            ← @Entity class
│   │   │
│   │   ├── hotel/                             # Core domain. Build after destination + image.
│   │   │   ├── [ ] HotelController.java           # guest-facing reads
│   │   │   ├── [ ] HotelManagementController.java  # host-facing writes
│   │   │   ├── [ ] HotelService.java
│   │   │   ├── [ ] HotelManagementService.java
│   │   │   ├── [ ] RoomTypeService.java
│   │   │   ├── [ ] HotelRepository.java
│   │   │   ├── [ ] RoomTypeRepository.java
│   │   │   ├── [ ] HotelMapper.java
│   │   │   ├── [ ] RoomTypeMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] HotelSummaryResponse.java  ← record
│   │   │   │   ├── [ ] HotelDetailResponse.java   ← record
│   │   │   │   ├── [ ] HotelCreateRequest.java    ← record
│   │   │   │   └── [ ] RoomTypeResponse.java      ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Hotel.java                 ← @Entity class
│   │   │   │   ├── [ ] HotelStatus.java           ← enum
│   │   │   │   ├── [ ] RoomType.java              ← @Entity class
│   │   │   │   ├── [ ] RatePolicy.java            ← @Entity class
│   │   │   │   └── [ ] DiscountPolicy.java        ← @Entity class
│   │   │   └── exception/
│   │   │       ├── [ ] HotelNotFoundException.java
│   │   │       └── [ ] RoomTypeNotFoundException.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 3 — Availability & Search ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── availability/                      # Build before search + booking. Both depend on it.
│   │   │   ├── [ ] AvailabilityService.java
│   │   │   ├── [ ] AvailabilityRepository.java
│   │   │   ├── dto/
│   │   │   │   └── [ ] AvailabilityRequest.java   ← record
│   │   │   ├── entity/
│   │   │   │   └── [ ] AvailabilityBlock.java     ← @Entity class
│   │   │   └── exception/
│   │   │       └── [ ] RoomUnavailableException.java
│   │   │
│   │   ├── search/                            # Build after availability. Queries it heavily.
│   │   │   ├── [ ] HotelSearchController.java
│   │   │   ├── [ ] HotelSearchService.java
│   │   │   ├── [ ] SearchHistoryService.java
│   │   │   ├── [ ] SearchHistoryRepository.java
│   │   │   └── dto/
│   │   │       ├── [ ] SearchResult.java          ← record
│   │   │       └── [ ] CitySearchQuery.java       ← record
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 4 — Booking & Payment     ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── payment/                           # Build before booking. Booking calls payment.
│   │   │   ├── [ ] PaymentController.java
│   │   │   ├── [ ] PaymentService.java
│   │   │   ├── [ ] PaymentRepository.java
│   │   │   ├── [ ] PaymentGatewayClient.java
│   │   │   ├── [ ] PaymentReconciliationScheduler.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] PaymentRequest.java        ← record
│   │   │   │   └── [ ] PaymentResponse.java       ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Payment.java               ← @Entity class
│   │   │   │   └── [ ] PaymentStatus.java         ← enum
│   │   │   └── exception/
│   │   │       └── [ ] PaymentFailedException.java
│   │   │
│   │   ├── booking/                           # Build after payment + availability.
│   │   │   ├── [ ] BookingController.java
│   │   │   ├── [ ] BookingService.java
│   │   │   ├── [ ] BookingRepository.java
│   │   │   ├── [ ] BookingExpiryScheduler.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] CreateBookingRequest.java  ← record
│   │   │   │   └── [ ] BookingResponse.java       ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Booking.java               ← @Entity class
│   │   │   │   └── [ ] BookingStatus.java         ← enum
│   │   │   └── exception/
│   │   │       ├── [ ] BookingNotFoundException.java
│   │   │       └── [ ] CancellationNotAllowedException.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 5 — Engagement Layer      ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── notification/                      # Build before review + promotion. Both trigger it.
│   │   │   ├── [ ] NotificationController.java
│   │   │   ├── [ ] NotificationService.java
│   │   │   ├── [ ] NotificationRepository.java
│   │   │   ├── dto/
│   │   │   │   └── [ ] NotificationResponse.java  ← record
│   │   │   └── entity/
│   │   │       ├── [ ] Notification.java          ← @Entity class
│   │   │       └── [ ] NotificationType.java      ← enum
│   │   │
│   │   ├── promotion/                         # Build after notification.
│   │   │   ├── [ ] PromoController.java
│   │   │   ├── [ ] PromotionService.java
│   │   │   ├── [ ] PromotionRepository.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] PromoValidateRequest.java  ← record
│   │   │   │   └── [ ] PromoValidateResponse.java ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Promotion.java             ← @Entity class
│   │   │   │   └── [ ] DiscountType.java          ← enum
│   │   │   └── exception/
│   │   │       └── [ ] InvalidPromoCodeException.java
│   │   │
│   │   ├── review/                            # Requires completed bookings to exist.
│   │   │   ├── [ ] ReviewController.java
│   │   │   ├── [ ] ReviewService.java
│   │   │   ├── [ ] ReviewRepository.java
│   │   │   ├── [ ] ReviewMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] CreateReviewRequest.java   ← record
│   │   │   │   └── [ ] ReviewResponse.java        ← record
│   │   │   ├── entity/
│   │   │   │   └── [ ] Review.java                ← @Entity class
│   │   │   └── exception/
│   │   │       ├── [ ] ReviewNotFoundException.java
│   │   │       └── [ ] UnverifiedBookingException.java
│   │   │
│   │   ├── recommendation/                    # Requires search history + hotel data.
│   │   │   ├── [ ] RecommendationController.java
│   │   │   └── [ ] RecommendationService.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 6 — Admin & Back-office   ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── admin/                             # Build last. Touches every other module.
│   │   │   ├── [ ] AdminController.java
│   │   │   └── [ ] AdminService.java
│   │   │
│   │   └── MiniAgodaApplication.java
│   │
│   ├── test/java/com/miniagoda/
│   │   ├── [ ] auth/
│   │   ├── [ ] user/
│   │   ├── [ ] hotel/
│   │   ├── [ ] booking/
│   │   ├── [ ] payment/
│   │   └── [ ] ...
│   │
│   └── main/resources/
│       ├── [ ] application.yml
│       └── db/migration/                      # Run in this order. Never skip a version.
│           ├── [ ] V1__create_users.sql
│           ├── [ ] V2__create_refresh_tokens.sql
│           ├── [ ] V3__create_cities.sql
│           ├── [ ] V4__create_hotels.sql
│           ├── [ ] V5__create_room_types.sql
│           ├── [ ] V6__create_hotel_images.sql
│           ├── [ ] V7__create_availability_blocks.sql
│           ├── [ ] V8__create_payments.sql
│           ├── [ ] V9__create_bookings.sql
│           ├── [ ] V10__create_notifications.sql
│           ├── [ ] V11__create_promotions.sql
│           ├── [ ] V12__create_reviews.sql
│           └── [ ] V13__create_search_history.sql
│
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── setup/
│   ├── flows/
│   ├── wiki/
│   ├── http.md
│   └── roadmap.md
│
└── README.md
```

---

## Phase summary

| Phase | Modules | Unlock |
|-------|---------|--------|
| 1 — Foundation & Auth | `common` → `user` → `auth` | Every other module |
| 2 — Core Hotel Data | `destination` → `image` → `hotel` | Search, availability, bookings |
| 3 — Availability & Search | `availability` → `search` | Booking, recommendations |
| 4 — Booking & Payment | `payment` → `booking` | Reviews, admin |
| 5 — Engagement | `notification` → `promotion` → `review` → `recommendation` | Admin |
| 6 — Admin | `admin` | Nothing — this is the end |