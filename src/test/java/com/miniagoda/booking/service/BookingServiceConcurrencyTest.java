package com.miniagoda.booking.service;

import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.exception.NotEnoughRoomsException;
import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.hotel.repository.RoomTypeRepository;
import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.user.entity.Role;
import com.miniagoda.user.entity.User;
import com.miniagoda.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceConcurrencyTest {

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;

    private static final int TOTAL_UNITS     = 5;
    private static final int ROOMS_REQUESTED = 1;
    private static final int THREADS         = 50;

    private RoomType roomType;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        // Delete in foreign key order — children first
        inventoryRepository.deleteAll();
        bookingRepository.deleteAll();
        roomTypeRepository.deleteAll();
        hotelRepository.deleteAll();
        userRepository.deleteAll();

        // Hotel
        Hotel hotel = new Hotel();
        hotel.setName("Test Hotel");
        hotel.setCity("Bangkok");
        hotel.setAddress("123 Test Street");
        hotel.setCode("TEST001");
        hotelRepository.save(hotel);

        // RoomType
        roomType = new RoomType();
        roomType.setName("Deluxe Room");
        roomType.setCapacity(2);
        roomType.setTotalUnits(TOTAL_UNITS);
        roomType.setPrice(new BigDecimal("1000.00"));
        roomType.setHotel(hotel);
        roomTypeRepository.save(roomType);

        // Inventory — 2 nights
        checkIn  = LocalDate.now().plusDays(1);
        checkOut = checkIn.plusDays(2);

        LocalDate cursor = checkIn;
        while (cursor.isBefore(checkOut)) {
            Inventory inv = new Inventory();
            inv.setRoomType(roomType);
            inv.setDate(cursor);
            inv.setAvailableUnits(TOTAL_UNITS);
            inventoryRepository.save(inv);
            cursor = cursor.plusDays(1);
        }

        // Users — one per thread
        for (int i = 0; i < THREADS; i++) {
            User user = new User();
            user.setFirstName("User");
            user.setLastName(String.valueOf(i));
            user.setEmail("user" + i + "@test.com");
            user.setPassword("password");
            user.setRole(Role.CUSTOMER);
            user.setVerified(true);
            userRepository.save(user);
        }
    }

    @Test
    @DisplayName("only 5 bookings succeed when 50 threads concurrently book a room with 5 units available")
    void onlyAvailableUnitsAreBooked() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);  // holds all threads until released
        CountDownLatch doneLatch  = new CountDownLatch(THREADS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < THREADS; i++) {
            final String email = "user" + i + "@test.com";
            executor.submit(() -> {
                try {
                    startLatch.await();  // all threads wait here until we say go

                    SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(email, null, List.of())
                    );

                    BookingRequest request = new BookingRequest(
                        roomType.getId(), checkIn, checkOut, ROOMS_REQUESTED
                    );

                    bookingService.createBooking(request);
                    successCount.incrementAndGet();

                } catch (NotEnoughRoomsException e) {
                    failCount.incrementAndGet();       // expected — not enough rooms
                } catch (Throwable t) {
                    unexpectedErrors.add(t);           // anything else is a real problem
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();                        // release all 50 threads simultaneously
        doneLatch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;

        executor.shutdown();

        System.out.printf("%n[CONCURRENCY TEST RESULTS]%n");
        System.out.printf("  Threads        : %d%n", THREADS);
        System.out.printf("  Succeeded      : %d%n", successCount.get());
        System.out.printf("  Rejected       : %d%n", failCount.get());
        System.out.printf("  Unexpected err : %d%n", unexpectedErrors.size());
        System.out.printf("  Duration       : %d ms%n", duration);

        // No unexpected errors
        assertThat(unexpectedErrors)
            .as("Unexpected errors during concurrent booking")
            .isEmpty();

        // Exactly TOTAL_UNITS bookings succeeded
        assertThat(successCount.get())
            .as("Exactly %d bookings should succeed", TOTAL_UNITS)
            .isEqualTo(TOTAL_UNITS);

        // The rest were correctly rejected
        assertThat(failCount.get())
            .as("Remaining %d threads should be rejected", THREADS - TOTAL_UNITS)
            .isEqualTo(THREADS - TOTAL_UNITS);

        // availableUnits must be exactly 0 — never negative
        List<Inventory> finalInventory = inventoryRepository
            .findByRoomTypeIdAndDateBetween(roomType.getId(), checkIn, checkOut.minusDays(1));

        finalInventory.forEach(inv ->
            assertThat(inv.getAvailableUnits())
                .as("availableUnits for date %s should be 0, not negative", inv.getDate())
                .isZero()
        );
    }
}