package com.miniagoda.hotel.service;

import com.miniagoda.hotel.dto.HotelDetailRequest;
import com.miniagoda.hotel.dto.HotelDetailResponse;
import com.miniagoda.hotel.dto.RoomTypeResponse;
import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private HotelService hotelService;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private UUID hotelId;
    private UUID roomTypeId1;
    private UUID roomTypeId2;

    private LocalDate checkIn;
    private LocalDate checkOut;

    private Hotel hotel;
    private RoomType roomType1;
    private RoomType roomType2;

    private HotelDetailRequest request;

    @BeforeEach
    void setUp() throws Exception {
        hotelId     = UUID.randomUUID();
        roomTypeId1 = UUID.randomUUID();
        roomTypeId2 = UUID.randomUUID();

        checkIn  = LocalDate.now().plusDays(1);
        checkOut = LocalDate.now().plusDays(3);

        hotel     = buildHotel(hotelId, "Grand Hotel", "123 Main St", new BigDecimal("4.5"));
        roomType1 = buildRoomType(roomTypeId1, "Standard", 2, new BigDecimal("100.00"), hotel);
        roomType2 = buildRoomType(roomTypeId2, "Deluxe",   4, new BigDecimal("200.00"), hotel);

        hotel.setRoomTypes(List.of(roomType1, roomType2));

        request = HotelDetailRequest.builder()
                .hotelId(hotelId)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .guests(2)
                .rooms(1)
                .build();
    }

    // ── Happy-path tests ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getHotelDetail — success scenarios")
    class Success {

        @Test
        @DisplayName("Returns hotel info with all available room types")
        void allRoomTypesAvailable() {
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
            when(inventoryService.isAvailable(eq(roomTypeId1), any(), any(), any(), any())).thenReturn(true);
            when(inventoryService.isAvailable(eq(roomTypeId2), any(), any(), any(), any())).thenReturn(true);
            when(inventoryService.getAvailableRooms(eq(roomTypeId1), any(), any())).thenReturn(5);
            when(inventoryService.getAvailableRooms(eq(roomTypeId2), any(), any())).thenReturn(3);

            HotelDetailResponse response = hotelService.getHotelDetail(request);

            assertThat(response.getHotelId()).isEqualTo(hotelId);
            assertThat(response.getHotelName()).isEqualTo("Grand Hotel");
            assertThat(response.getHotelAddress()).isEqualTo("123 Main St");
            assertThat(response.getRating()).isEqualByComparingTo(new BigDecimal("4.5"));
            assertThat(response.getRoomTypes()).hasSize(2);
        }

        @Test
        @DisplayName("Filters out unavailable room types")
        void onlyAvailableRoomTypesIncluded() {
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
            when(inventoryService.isAvailable(eq(roomTypeId1), any(), any(), any(), any())).thenReturn(true);
            when(inventoryService.isAvailable(eq(roomTypeId2), any(), any(), any(), any())).thenReturn(false);
            when(inventoryService.getAvailableRooms(eq(roomTypeId1), any(), any())).thenReturn(5);

            HotelDetailResponse response = hotelService.getHotelDetail(request);

            assertThat(response.getRoomTypes()).hasSize(1);
            assertThat(response.getRoomTypes().get(0).getRoomTypeName()).isEqualTo("Standard");
        }

        @Test
        @DisplayName("Returns empty room list when no room types are available")
        void noRoomTypesAvailable() {
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
            when(inventoryService.isAvailable(any(), any(), any(), any(), any())).thenReturn(false);

            HotelDetailResponse response = hotelService.getHotelDetail(request);

            assertThat(response.getRoomTypes()).isEmpty();
            // getAvailableRooms should never be called when isAvailable returns false
            verify(inventoryService, never()).getAvailableRooms(any(), any(), any());
        }

        @Test
        @DisplayName("RoomTypeResponse is populated with correct field values")
        void roomTypeResponseFieldsAreCorrect() {
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
            when(inventoryService.isAvailable(eq(roomTypeId1), any(), any(), any(), any())).thenReturn(true);
            when(inventoryService.isAvailable(eq(roomTypeId2), any(), any(), any(), any())).thenReturn(false);
            when(inventoryService.getAvailableRooms(eq(roomTypeId1), any(), any())).thenReturn(4);

            HotelDetailResponse response = hotelService.getHotelDetail(request);

            RoomTypeResponse rt = response.getRoomTypes().get(0);
            assertThat(rt.getRoomTypeId()).isEqualTo(roomTypeId1);
            assertThat(rt.getRoomTypeName()).isEqualTo("Standard");
            assertThat(rt.getCapacity()).isEqualTo(2);
            assertThat(rt.getPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(rt.getAvailableRooms()).isEqualTo(4);
        }

        @Test
        @DisplayName("Hotel with no room types returns empty room list")
        void hotelWithNoRoomTypes() {
            hotel.setRoomTypes(List.of());
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

            HotelDetailResponse response = hotelService.getHotelDetail(request);

            assertThat(response.getRoomTypes()).isEmpty();
            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("Passes correct arguments to InventoryService")
        void correctArgumentsPassedToInventoryService() {
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
            when(inventoryService.isAvailable(any(), any(), any(), any(), any())).thenReturn(true);
            when(inventoryService.getAvailableRooms(any(), any(), any())).thenReturn(2);

            hotelService.getHotelDetail(request);

            verify(inventoryService).isAvailable(roomTypeId1, checkIn, checkOut, 2, 1);
            verify(inventoryService).isAvailable(roomTypeId2, checkIn, checkOut, 2, 1);
            verify(inventoryService).getAvailableRooms(roomTypeId1, checkIn, checkOut);
            verify(inventoryService).getAvailableRooms(roomTypeId2, checkIn, checkOut);
        }

        @Test
        @DisplayName("Hotel with null rating is returned without error")
        void hotelWithNullRating() throws Exception {
            hotel = buildHotel(hotelId, "Budget Inn", "456 Side St", null);
            hotel.setRoomTypes(List.of());
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

            HotelDetailResponse response = hotelService.getHotelDetail(request);

            assertThat(response.getRating()).isNull();
        }
    }

    // ── Error-path tests ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getHotelDetail — error scenarios")
    class Errors {

        @Test
        @DisplayName("Throws RuntimeException when hotel is not found")
        void hotelNotFound() {
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> hotelService.getHotelDetail(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Hotel not found!");

            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("Propagates exception thrown by InventoryService")
        void inventoryServiceThrows() {
            when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
            when(inventoryService.isAvailable(any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Inventory unavailable"));

            assertThatThrownBy(() -> hotelService.getHotelDetail(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Inventory unavailable");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a Hotel and injects the given UUID via reflection,
     * since BaseEntity generates the ID with @GeneratedValue.
     */
    private Hotel buildHotel(UUID id, String name, String address, BigDecimal rating)
            throws Exception {
        Hotel h = new Hotel();
        h.setName(name);
        h.setAddress(address);
        h.setCity("Bangkok");
        h.setCode("HTL-" + id.toString().substring(0, 8));
        h.setRating(rating);
        injectId(h, id);
        return h;
    }

    /**
     * Builds a RoomType and injects the given UUID via reflection.
     */
    private RoomType buildRoomType(UUID id, String name, int capacity, BigDecimal price, Hotel hotel)
            throws Exception {
        RoomType rt = new RoomType();
        rt.setName(name);
        rt.setCapacity(capacity);
        rt.setPrice(price);
        rt.setTotalUnits(10);
        rt.setHotel(hotel);
        injectId(rt, id);
        return rt;
    }

    /**
     * Reflectively sets the private {@code id} field declared in BaseEntity.
     */
    private void injectId(Object entity, UUID id) throws Exception {
        var field = com.miniagoda.common.entity.BaseEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}