package com.miniagoda.booking.service;

import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.dto.BookingResponse;
import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.exception.BookingNotFoundException;
import com.miniagoda.booking.exception.InventoryIncompleteException;
import com.miniagoda.booking.exception.NotEnoughRoomsException;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;
import com.miniagoda.user.entity.User;
import com.miniagoda.user.exception.UserNotFoundException;
import com.miniagoda.user.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    // Security mocks
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    private static final String USER_EMAIL = "user@example.com";
    private static final UUID ROOM_TYPE_ID = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        lenient().when(securityContext.getAuthentication())
                .thenReturn(authentication);

        lenient().when(authentication.getName())
                .thenReturn(USER_EMAIL);

        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sets the private {@code id} field declared in BaseEntity via reflection,
     * since it has no public setter (managed by JPA @GeneratedValue).
     */
    private static void setId(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not set id on " + entity.getClass().getSimpleName(), e);
        }
    }

    private User makeUser() {
        User user = new User();
        setId(user, UUID.randomUUID());
        user.setEmail(USER_EMAIL);
        return user;
    }

    private RoomType makeRoomType(BigDecimal pricePerNight) {
        RoomType rt = new RoomType();
        setId(rt, ROOM_TYPE_ID);
        rt.setName("Deluxe Room");
        rt.setPrice(pricePerNight);
        return rt;
    }

    /**
     * Builds a list of Inventory records covering [checkIn, checkOut).
     * Each record has the given availableUnits and all share the same RoomType.
     */
    private List<Inventory> makeInventories(
            LocalDate checkIn, LocalDate checkOut,
            int availableUnits, RoomType roomType) {

        List<Inventory> list = new java.util.ArrayList<>();
        LocalDate cursor = checkIn;
        while (cursor.isBefore(checkOut)) {
            Inventory inv = new Inventory();
            inv.setDate(cursor);
            inv.setAvailableUnits(availableUnits);
            inv.setRoomType(roomType);
            list.add(inv);
            cursor = cursor.plusDays(1);
        }
        return list;
    }

    private BookingRequest makeRequest(LocalDate checkIn, LocalDate checkOut, int rooms) {
        return new BookingRequest(ROOM_TYPE_ID, checkIn, checkOut, rooms);
    }

    // =========================================================================
    // createBooking
    // =========================================================================

    @Nested
    @DisplayName("createBooking")
    class CreateBooking {

        @Test
        @DisplayName("creates booking, decrements inventory, and returns correct response")
        void happyPath() {
            // Arrange
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(3);           // 3 nights
            int rooms = 2;

            RoomType roomType = makeRoomType(new BigDecimal("1000.00"));
            List<Inventory> inventories = makeInventories(checkIn, checkOut, 5, roomType);
            User user = makeUser();

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    eq(ROOM_TYPE_ID), eq(checkIn), eq(checkOut.minusDays(1))))
                .thenReturn(inventories);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            // Act
            BookingResponse response = bookingService.createBooking(makeRequest(checkIn, checkOut, rooms));

            // Assert – response fields
            assertThat(response.getRoomTypeId()).isEqualTo(ROOM_TYPE_ID);
            assertThat(response.getRoomTypeName()).isEqualTo("Deluxe Room");
            assertThat(response.getCheckIn()).isEqualTo(checkIn);
            assertThat(response.getCheckOut()).isEqualTo(checkOut);
            assertThat(response.getRoomsBooked()).isEqualTo(rooms);
            assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(response.getExpiredAt()).isAfter(LocalDateTime.now());

            // total = 1000 * 3 nights * 2 rooms = 6000
            assertThat(response.getTotalPrice()).isEqualByComparingTo(new BigDecimal("6000.00"));

            // Assert – inventory decremented for each day
            inventories.forEach(inv ->
                assertThat(inv.getAvailableUnits()).isEqualTo(3)); // 5 - 2

            verify(inventoryRepository).saveAll(inventories);

            // Assert – booking persisted with correct currency and user
            ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(bookingCaptor.capture());
            Booking saved = bookingCaptor.getValue();
            assertThat(saved.getCurrency()).isEqualTo("THB");
            assertThat(saved.getUser()).isEqualTo(user);
        }

        @Test
        @DisplayName("throws InventoryIncompleteException when inventory records count doesn't match stay duration")
        void throwsWhenInventoryIncomplete() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(3);

            RoomType roomType = makeRoomType(new BigDecimal("1000.00"));
            // Only 2 days of inventory for a 3-night stay
            List<Inventory> partial = makeInventories(checkIn, checkOut.minusDays(1), 5, roomType);

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(partial);

            assertThatThrownBy(() ->
                bookingService.createBooking(makeRequest(checkIn, checkOut, 1)))
                .isInstanceOf(InventoryIncompleteException.class)
                .hasMessage("Inventory missing for some dates");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InventoryIncompleteException when inventory list is empty")
        void throwsWhenInventoryEmpty() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(2);

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

            assertThatThrownBy(() ->
                bookingService.createBooking(makeRequest(checkIn, checkOut, 1)))
                .isInstanceOf(InventoryIncompleteException.class);
        }

        @Test
        @DisplayName("throws NotEnoughRoomsException when min available units < rooms requested")
        void throwsWhenNotEnoughRooms() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(2);

            RoomType roomType = makeRoomType(new BigDecimal("500.00"));
            List<Inventory> inventories = makeInventories(checkIn, checkOut, 1, roomType);

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(inventories);

            assertThatThrownBy(() ->
                bookingService.createBooking(makeRequest(checkIn, checkOut, 2)))
                .isInstanceOf(NotEnoughRoomsException.class)
                .hasMessage("Not enough rooms available");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws NotEnoughRoomsException when one day has fewer units than the rest (bottleneck day)")
        void throwsWhenBottleneckDayHasInsufficientUnits() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(3);

            RoomType roomType = makeRoomType(new BigDecimal("800.00"));
            List<Inventory> inventories = makeInventories(checkIn, checkOut, 5, roomType);
            // Middle day has only 1 room left
            inventories.get(1).setAvailableUnits(1);

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(inventories);

            assertThatThrownBy(() ->
                bookingService.createBooking(makeRequest(checkIn, checkOut, 2)))
                .isInstanceOf(NotEnoughRoomsException.class);
        }

        @Test
        @DisplayName("succeeds when rooms requested equals exactly the minimum available units")
        void succeedsWhenRoomsRequestedEqualsMinAvailable() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(2);

            RoomType roomType = makeRoomType(new BigDecimal("500.00"));
            List<Inventory> inventories = makeInventories(checkIn, checkOut, 3, roomType);
            User user = makeUser();

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(inventories);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            BookingResponse response = bookingService.createBooking(makeRequest(checkIn, checkOut, 3));

            assertThat(response.getRoomsBooked()).isEqualTo(3);
            inventories.forEach(inv -> assertThat(inv.getAvailableUnits()).isZero());
        }

        @Test
        @DisplayName("throws UserNotFoundException when authenticated user is not in the database")
        void throwsWhenUserNotFound() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(1);

            RoomType roomType = makeRoomType(new BigDecimal("500.00"));
            List<Inventory> inventories = makeInventories(checkIn, checkOut, 5, roomType);

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(inventories);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                bookingService.createBooking(makeRequest(checkIn, checkOut, 1)))
                .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("booking expiry is set approximately 15 minutes from now")
        void bookingExpiryIsSetTo15Minutes() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(1);

            RoomType roomType = makeRoomType(new BigDecimal("500.00"));
            List<Inventory> inventories = makeInventories(checkIn, checkOut, 5, roomType);
            User user = makeUser();

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(inventories);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            LocalDateTime before = LocalDateTime.now().plusMinutes(15);
            BookingResponse response = bookingService.createBooking(makeRequest(checkIn, checkOut, 1));
            LocalDateTime after = LocalDateTime.now().plusMinutes(15);

            assertThat(response.getExpiredAt())
                .isAfterOrEqualTo(before.minusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(1));
        }

        @Test
        @DisplayName("calculates total price correctly: pricePerNight * nights * rooms")
        void calculatesTotalPriceCorrectly() {
            LocalDate checkIn  = LocalDate.now().plusDays(1);
            LocalDate checkOut = checkIn.plusDays(5);   // 5 nights

            RoomType roomType = makeRoomType(new BigDecimal("250.50"));
            List<Inventory> inventories = makeInventories(checkIn, checkOut, 10, roomType);
            User user = makeUser();

            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                .thenReturn(inventories);
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));

            BookingResponse response = bookingService.createBooking(makeRequest(checkIn, checkOut, 4));

            // 250.50 * 5 * 4 = 5010.00
            assertThat(response.getTotalPrice()).isEqualByComparingTo(new BigDecimal("5010.00"));
        }
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns booking when it exists")
        void returnsBookingWhenFound() {
            UUID id = UUID.randomUUID();
            Booking booking = new Booking();
            setId(booking, id);

            when(bookingRepository.findById(id)).thenReturn(Optional.of(booking));

            Booking result = bookingService.findById(id);

            assertThat(result).isEqualTo(booking);
        }

        @Test
        @DisplayName("throws BookingNotFoundException when booking does not exist")
        void throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(bookingRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.findById(id))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(id.toString());
        }
    }

    // =========================================================================
    // getBookings
    // =========================================================================

    @Nested
    @DisplayName("getBookings")
    class GetBookings {

        @Test
        @DisplayName("returns mapped responses for all bookings belonging to the authenticated user")
        void returnsMappedBookings() {
            User user = makeUser();
            RoomType roomType = makeRoomType(new BigDecimal("500.00"));

            Booking b1 = makeBooking(roomType, user, BookingStatus.PENDING);
            Booking b2 = makeBooking(roomType, user, BookingStatus.CONFIRMED);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookingRepository.findByUserId(user.getId())).thenReturn(List.of(b1, b2));

            List<BookingResponse> responses = bookingService.getBookings();

            assertThat(responses).hasSize(2);
            assertThat(responses)
                .extracting(BookingResponse::getStatus)
                .containsExactly(BookingStatus.PENDING, BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("returns empty list when user has no bookings")
        void returnsEmptyListWhenNoBookings() {
            User user = makeUser();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookingRepository.findByUserId(user.getId())).thenReturn(Collections.emptyList());

            List<BookingResponse> responses = bookingService.getBookings();

            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("throws UserNotFoundException when authenticated user is not in the database")
        void throwsWhenUserNotFound() {
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.getBookings())
                .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("response fields are correctly mapped from booking entity")
        void responseFieldsMappedCorrectly() {
            User user = makeUser();
            RoomType roomType = makeRoomType(new BigDecimal("300.00"));

            LocalDate checkIn  = LocalDate.now().plusDays(2);
            LocalDate checkOut = checkIn.plusDays(3);

            Booking booking = new Booking();
            setId(booking, UUID.randomUUID());
            booking.setRoomType(roomType);
            booking.setUser(user);
            booking.setCheckIn(checkIn);
            booking.setCheckOut(checkOut);
            booking.setRoomsBooked(2);
            booking.setTotalPrice(new BigDecimal("1800.00"));
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setExpiredAt(LocalDateTime.now().plusMinutes(15));

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(bookingRepository.findByUserId(user.getId())).thenReturn(List.of(booking));

            BookingResponse response = bookingService.getBookings().get(0);

            assertThat(response.getBookingId()).isEqualTo(booking.getId());
            assertThat(response.getRoomTypeId()).isEqualTo(ROOM_TYPE_ID);
            assertThat(response.getRoomTypeName()).isEqualTo("Deluxe Room");
            assertThat(response.getCheckIn()).isEqualTo(checkIn);
            assertThat(response.getCheckOut()).isEqualTo(checkOut);
            assertThat(response.getRoomsBooked()).isEqualTo(2);
            assertThat(response.getTotalPrice()).isEqualByComparingTo("1800.00");
            assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        // Helper used only by getBookings tests
        private Booking makeBooking(RoomType roomType, User user, BookingStatus status) {
            Booking b = new Booking();
            setId(b, UUID.randomUUID());
            b.setRoomType(roomType);
            b.setUser(user);
            b.setCheckIn(LocalDate.now().plusDays(1));
            b.setCheckOut(LocalDate.now().plusDays(3));
            b.setRoomsBooked(1);
            b.setTotalPrice(new BigDecimal("1000.00"));
            b.setStatus(status);
            b.setExpiredAt(LocalDateTime.now().plusMinutes(15));
            return b;
        }
    }
}