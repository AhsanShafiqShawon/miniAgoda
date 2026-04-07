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
│   │   ├── common/                                     # Build this first. Everything depends on it.
│   │   │   ├── config/
│   │   │   │   ├── [x] AppConfig.java
│   │   │   │   ├── [x] SecurityConfig.java
│   │   │   │   ├── [ ] JwtAuthenticationFilter.java
│   │   │   │   └── [x] JwtConfig.java
│   │   │   ├── exception/
│   │   │   │   ├── [ ] GlobalExceptionHandler.java
│   │   │   │   ├── [ ] NotFoundException.java
│   │   │   │   ├── [ ] ConflictException.java
│   │   │   │   ├── [ ] UnauthorizedException.java
│   │   │   │   ├── [ ] ForbiddenException.java
│   │   │   │   └── [ ] ValidationException.java
│   │   │   ├── response/
│   │   │   │   ├── [x] ApiResponse.java
│   │   │   │   └── [x] ErrorResponse.java
│   │   │   └── util/
│   │   │       └── [x] JwtUtil.java
│   │   │
│   │   ├── user/                                       # Build before auth. Auth depends on User.
│   │   │   ├── [ ] UserController.java
│   │   │   ├── [ ] UserService.java
│   │   │   ├── [ ] UserRepository.java
│   │   │   ├── [ ] UserMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] UserSummary.java                ← record
│   │   │   │   ├── [ ] EditUserRequest.java            ← record
│   │   │   │   └── [ ] ChangePasswordRequest.java      ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] User.java                       ← @Entity class
│   │   │   │   ├── [ ] UserRole.java                   ← enum: GUEST, HOST, ADMIN
│   │   │   │   └── [ ] UserStatus.java                 ← enum: ACTIVE, SUSPENDED, UNVERIFIED
│   │   │   ├── value/
│   │   │   │   └── [ ] PhoneNumber.java                ← value object
│   │   │   └── exception/
│   │   │       └── [ ] UserNotFoundException.java
│   │   │
│   │   ├── auth/                                       # Build after user. Depends on User entity.
│   │   │   ├── [ ] AuthController.java
│   │   │   ├── [ ] AuthService.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] AuthRequest.java                ← record
│   │   │   │   ├── [ ] AuthResponse.java               ← record
│   │   │   │   └── [ ] RegisterRequest.java            ← record
│   │   │   ├── entity/
│   │   │   │   └── [ ] RefreshToken.java               ← @Entity class
│   │   │   └── exception/
│   │   │       ├── [ ] InvalidTokenException.java
│   │   │       └── [ ] TokenExpiredException.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 2 — Core Hotel Data       ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── destination/                                # Build before hotel. Hotels reference cities.
│   │   │   ├── [ ] DestinationController.java
│   │   │   ├── [ ] DestinationService.java
│   │   │   ├── [ ] DestinationRepository.java
│   │   │   ├── [ ] DestinationMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] AddDestinationRequest.java      ← record
│   │   │   │   └── [ ] EditDestinationRequest.java     ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Destination.java                ← @Entity class
│   │   │   │   ├── [ ] Country.java                    ← @Entity class
│   │   │   │   ├── [ ] City.java                       ← @Entity class
│   │   │   │   └── [ ] DestinationStatus.java          ← enum
│   │   │   └── exception/
│   │   │       └── [ ] DestinationNotFoundException.java
│   │   │
│   │   ├── image/                                      # Build before hotel. Hotels reference images.
│   │   │   ├── [ ] ImageController.java
│   │   │   ├── [ ] ImageService.java
│   │   │   ├── [ ] ImageRepository.java
│   │   │   ├── gateway/
│   │   │   │   ├── [ ] StorageGateway.java             ← interface
│   │   │   │   ├── s3/
│   │   │   │   │   └── [ ] S3StorageGateway.java       ← production implementation
│   │   │   │   └── local/
│   │   │   │       └── [ ] LocalStorageGateway.java    ← dev/test implementation
│   │   │   ├── dto/
│   │   │   │   └── [ ] ImageUploadRequest.java         ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Image.java                      ← @Entity class
│   │   │   │   ├── [ ] ImageStatus.java                ← enum
│   │   │   │   ├── [ ] ImageEntityType.java            ← enum
│   │   │   │   └── [ ] ContentType.java                ← enum
│   │   │   └── exception/
│   │   │       └── [ ] ImageNotFoundException.java
│   │   │
│   │   ├── hotel/                                      # Core domain. Build after destination + image.
│   │   │   ├── [ ] HotelController.java                # guest-facing reads
│   │   │   ├── [ ] HotelManagementController.java      # host-facing writes
│   │   │   ├── [ ] HotelService.java
│   │   │   ├── [ ] HotelManagementService.java
│   │   │   ├── [ ] RoomTypeService.java
│   │   │   ├── [ ] HotelRepository.java
│   │   │   ├── [ ] RoomTypeRepository.java
│   │   │   ├── [ ] HotelMapper.java
│   │   │   ├── [ ] RoomTypeMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] HotelSummary.java               ← record
│   │   │   │   ├── [ ] AddHotelRequest.java            ← record
│   │   │   │   ├── [ ] EditHotelRequest.java           ← record
│   │   │   │   ├── [ ] AddRoomTypeRequest.java         ← record
│   │   │   │   ├── [ ] EditRoomTypeRequest.java        ← record
│   │   │   │   ├── [ ] AddRatePolicyRequest.java       ← record
│   │   │   │   └── [ ] EditRatePolicyRequest.java      ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Hotel.java                      ← @Entity class
│   │   │   │   ├── [ ] HotelStatus.java                ← enum
│   │   │   │   ├── [ ] RoomType.java                   ← @Entity class
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
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 3 — Availability & Search ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── availability/                               # Build before search + booking. Both depend on it.
│   │   │   ├── [ ] AvailabilityService.java
│   │   │   ├── [ ] AvailabilityRepository.java
│   │   │   ├── dto/
│   │   │   │   └── [ ] RoomTypeAvailability.java       ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] AvailabilityBlock.java          ← @Entity class
│   │   │   │   └── [ ] AvailabilityStatus.java         ← enum
│   │   │   └── exception/
│   │   │       └── [ ] RoomUnavailableException.java
│   │   │
│   │   ├── search/                                     # Build after availability. Queries it heavily.
│   │   │   ├── [ ] SearchController.java
│   │   │   ├── [ ] HotelSearchService.java
│   │   │   ├── [ ] SearchHistoryService.java
│   │   │   ├── [ ] SearchHistoryRepository.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] CitySearchQuery.java            ← record
│   │   │   │   ├── [ ] HotelSearchQuery.java           ← record
│   │   │   │   └── [ ] SearchResult.java               ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] SearchHistory.java              ← @Entity class
│   │   │   │   └── [ ] SearchHistoryStatus.java        ← enum
│   │   │   └── exception/
│   │   │       └── [ ] SearchHistoryNotFoundException.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 4 — Booking & Payment     ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── payment/                                    # Build before booking. Booking calls payment.
│   │   │   ├── [ ] PaymentController.java
│   │   │   ├── [ ] PaymentService.java
│   │   │   ├── [ ] PaymentRepository.java
│   │   │   ├── [ ] RefundRepository.java
│   │   │   ├── [ ] PaymentReconciliationScheduler.java
│   │   │   ├── gateway/
│   │   │   │   ├── [ ] PaymentGateway.java             ← interface
│   │   │   │   └── stripe/
│   │   │   │       └── [ ] StripePaymentGateway.java   ← implementation
│   │   │   ├── dto/
│   │   │   │   └── [ ] CreatePaymentRequest.java       ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Payment.java                    ← @Entity class
│   │   │   │   ├── [ ] PaymentStatus.java              ← enum
│   │   │   │   ├── [ ] PaymentMethod.java              ← enum
│   │   │   │   ├── [ ] Refund.java                     ← @Entity class
│   │   │   │   └── [ ] RefundStatus.java               ← enum
│   │   │   └── exception/
│   │   │       ├── [ ] PaymentFailedException.java
│   │   │       └── [ ] RefundFailedException.java
│   │   │
│   │   ├── booking/                                    # Build after payment + availability.
│   │   │   ├── [ ] BookingController.java
│   │   │   ├── [ ] BookingService.java
│   │   │   ├── [ ] BookingRepository.java
│   │   │   ├── [ ] BookingMapper.java
│   │   │   ├── [ ] BookingExpiryScheduler.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] CreateBookingRequest.java       ← record
│   │   │   │   ├── [ ] EditBookingRequest.java         ← record
│   │   │   │   └── [ ] BookingSummary.java             ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Booking.java                    ← @Entity class
│   │   │   │   └── [ ] BookingStatus.java              ← enum
│   │   │   └── exception/
│   │   │       ├── [ ] BookingNotFoundException.java
│   │   │       └── [ ] CancellationNotAllowedException.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 5 — Engagement Layer      ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── notification/                               # Build before review + promotion. Both trigger it.
│   │   │   ├── [ ] NotificationController.java
│   │   │   ├── [ ] NotificationService.java
│   │   │   ├── [ ] NotificationRepository.java
│   │   │   ├── gateway/
│   │   │   │   ├── [ ] EmailGateway.java               ← interface
│   │   │   │   ├── sendgrid/
│   │   │   │   │   └── [ ] SendGridEmailGateway.java   ← production implementation
│   │   │   │   └── mock/
│   │   │   │       └── [ ] MockEmailGateway.java       ← test implementation
│   │   │   ├── dto/
│   │   │   │   └── [ ] CreateNotificationRequest.java  ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Notification.java               ← @Entity class
│   │   │   │   ├── [ ] NotificationType.java           ← enum
│   │   │   │   ├── [ ] NotificationStatus.java         ← enum
│   │   │   │   ├── [ ] NotificationReadStatus.java     ← enum
│   │   │   │   └── [ ] Channel.java                    ← enum
│   │   │   └── exception/
│   │   │       └── [ ] NotificationNotFoundException.java
│   │   │
│   │   ├── promotion/                                  # Build after notification.
│   │   │   ├── [ ] PromotionController.java
│   │   │   ├── [ ] PromotionService.java
│   │   │   ├── [ ] PromotionRepository.java
│   │   │   ├── [ ] PromotionMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] CreatePromotionRequest.java     ← record
│   │   │   │   ├── [ ] EditPromotionRequest.java       ← record
│   │   │   │   └── [ ] ValidatePromotionResult.java    ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Promotion.java                  ← @Entity class
│   │   │   │   ├── [ ] PromotionStatus.java            ← enum
│   │   │   │   ├── [ ] PromotionScope.java             ← enum
│   │   │   │   └── [ ] DiscountType.java               ← enum
│   │   │   └── exception/
│   │   │       └── [ ] InvalidPromoCodeException.java
│   │   │
│   │   ├── review/                                     # Requires completed bookings to exist.
│   │   │   ├── [ ] ReviewController.java
│   │   │   ├── [ ] ReviewService.java
│   │   │   ├── [ ] ReviewRepository.java
│   │   │   ├── [ ] ReviewMapper.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] CreateReviewRequest.java        ← record
│   │   │   │   └── [ ] EditReviewRequest.java          ← record
│   │   │   ├── entity/
│   │   │   │   ├── [ ] Review.java                     ← @Entity class
│   │   │   │   └── [ ] ReviewStatus.java               ← enum
│   │   │   ├── value/
│   │   │   │   └── [ ] ReviewRating.java               ← value object
│   │   │   └── exception/
│   │   │       ├── [ ] ReviewNotFoundException.java
│   │   │       └── [ ] UnverifiedBookingException.java
│   │   │
│   │   ├── recommendation/                             # Requires search history + hotel data.
│   │   │   ├── [ ] RecommendationController.java
│   │   │   └── [ ] RecommendationService.java
│   │   │
│   │   │   ╔══════════════════════════════════╗
│   │   │   ║  Phase 6 — Admin & Back-office   ║
│   │   │   ╚══════════════════════════════════╝
│   │   │
│   │   ├── admin/                                      # Build last. Touches every other module.
│   │   │   ├── [ ] AdminController.java
│   │   │   ├── [ ] AdminService.java
│   │   │   ├── dto/
│   │   │   │   ├── [ ] SystemStats.java                ← record
│   │   │   │   ├── [ ] Revenue.java                    ← record
│   │   │   │   ├── [ ] OccupancyRate.java              ← record
│   │   │   │   └── [ ] RevenueScope.java               ← record
│   │   │   └── entity/
│   │   │       ├── [ ] RevenueScopeType.java           ← enum
│   │   │       ├── [ ] RevenuePeriod.java              ← enum
│   │   │       └── [ ] ModerationAction.java           ← enum
│   │   │
│   │   └── MiniAgodaApplication.java
│   │
│   ├── test/java/com/miniagoda/
│   │   ├── [ ] auth/
│   │   ├── [ ] user/
│   │   ├── [ ] hotel/
│   │   ├── [ ] search/
│   │   ├── [ ] availability/
│   │   ├── [ ] booking/
│   │   ├── [ ] payment/
│   │   ├── [ ] notification/
│   │   ├── [ ] promotion/
│   │   ├── [ ] review/
│   │   └── [ ] recommendation/
│   │
│   └── main/resources/
│       ├── [ ] application.yml
│       └── db/migration/                               # Run in this order. Never skip a version.
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
├── prose/
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── setup/
│   ├── flows/
│   ├── wiki/
│   ├── http.md
│   ├── implementation-progress.md
│   └── roadmap.md
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