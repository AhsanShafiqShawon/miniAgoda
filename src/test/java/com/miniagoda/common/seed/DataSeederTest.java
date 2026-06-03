package com.miniagoda.common.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.hotel.repository.RoomTypeRepository;
import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private InventoryRepository inventoryRepository;

    // Real ObjectMapper — we want actual JSON deserialization, not a mock
    private final ObjectMapper objectMapper = new ObjectMapper();

    private DataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DataSeeder(roomTypeRepository, objectMapper, hotelRepository, inventoryRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Hotel hotelWithCode(String code) {
        Hotel hotel = new Hotel();
        hotel.setCode(code);
        hotel.setName("Test Hotel");
        hotel.setCity("Bangkok");
        hotel.setAddress("123 Test Rd");
        return hotel;
    }

    private RoomType roomTypeWithUnits(int totalUnits) {
        RoomType rt = new RoomType();
        rt.setName("Deluxe Room");
        rt.setCapacity(2);
        rt.setPrice(BigDecimal.valueOf(5000));
        rt.setTotalUnits(totalUnits);
        rt.setHotel(hotelWithCode("BKK-TST-001"));
        return rt;
    }

    // -------------------------------------------------------------------------
    // roomTypesSeed() tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("roomTypesSeed()")
    class RoomTypesSeed {

        @Test
        @DisplayName("skips seeding when room types already exist")
        void run_roomTypesExist_skipsRoomTypeSeed() throws Exception {
            when(roomTypeRepository.count()).thenReturn(1L);
            when(inventoryRepository.count()).thenReturn(1L);

            seeder.run();

            verify(roomTypeRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("seeds room types from room_types.json when table is empty")
        void run_roomTypesEmpty_savesRoomTypes() throws Exception {
            when(roomTypeRepository.count()).thenReturn(0L);
            when(inventoryRepository.count()).thenReturn(1L);

            // Provide a hotel stub for every unique hotelCode in room_types.json
            for (String code : List.of(
                    "BKK-PEN-001", "BKK-MAN-002", "BKK-CAP-003", "BKK-ANA-004",
                    "BKK-CEN-005", "BKK-CHA-006", "BKK-IBI-007", "BKK-NOV-008",
                    "BKK-SOB-009", "BKK-IND-010")) {
                when(hotelRepository.findByCode(code)).thenReturn(Optional.of(hotelWithCode(code)));
            }

            seeder.run();

            ArgumentCaptor<List<RoomType>> captor = ArgumentCaptor.captor();
            verify(roomTypeRepository).saveAll(captor.capture());

            List<RoomType> saved = captor.getValue();
            // room_types.json has 30 entries (3 room types × 10 hotels)
            assertThat(saved).hasSize(30);
        }

        @Test
        @DisplayName("maps name, capacity, price, totalUnits and hotel correctly from JSON")
        void run_roomTypesEmpty_mapsFieldsCorrectly() throws Exception {
            when(roomTypeRepository.count()).thenReturn(0L);
            when(inventoryRepository.count()).thenReturn(1L);

            for (String code : List.of(
                    "BKK-PEN-001", "BKK-MAN-002", "BKK-CAP-003", "BKK-ANA-004",
                    "BKK-CEN-005", "BKK-CHA-006", "BKK-IBI-007", "BKK-NOV-008",
                    "BKK-SOB-009", "BKK-IND-010")) {
                when(hotelRepository.findByCode(code)).thenReturn(Optional.of(hotelWithCode(code)));
            }

            seeder.run();

            ArgumentCaptor<List<RoomType>> captor = ArgumentCaptor.captor();
            verify(roomTypeRepository).saveAll(captor.capture());

            // Spot-check the first entry from room_types.json:
            // { "name": "Deluxe River View", "capacity": 2, "price": 18000.00,
            //   "totalUnits": 20, "hotelCode": "BKK-PEN-001" }
            RoomType first = captor.getValue().get(0);
            assertThat(first.getName()).isEqualTo("Deluxe River View");
            assertThat(first.getCapacity()).isEqualTo(2);
            assertThat(first.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(18000.00));
            assertThat(first.getTotalUnits()).isEqualTo(20);
            assertThat(first.getHotel().getCode()).isEqualTo("BKK-PEN-001");
        }

        @Test
        @DisplayName("throws RuntimeException when a hotelCode in the JSON has no matching hotel")
        void run_unknownHotelCode_throwsRuntimeException() throws Exception {
            when(roomTypeRepository.count()).thenReturn(0L);
            // No inventoryRepository stub needed — exception is thrown during roomTypesSeed()
            // before inventorySeed() is ever reached

            // Return empty for every lookup — simulates missing hotel data
            when(hotelRepository.findByCode(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> seeder.run())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Hotel not found");
        }
    }

    // -------------------------------------------------------------------------
    // inventorySeed() tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("inventorySeed()")
    class InventorySeed {

        @Test
        @DisplayName("skips seeding when inventory already exists")
        void run_inventoryExists_skipsInventorySeed() throws Exception {
            when(roomTypeRepository.count()).thenReturn(1L);
            when(inventoryRepository.count()).thenReturn(1L);

            seeder.run();

            verify(inventoryRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("creates 365 inventory entries per room type")
        void run_inventoryEmpty_creates365EntriesPerRoomType() throws Exception {
            when(roomTypeRepository.count()).thenReturn(1L);
            when(inventoryRepository.count()).thenReturn(0L);

            RoomType rt1 = roomTypeWithUnits(10);
            RoomType rt2 = roomTypeWithUnits(5);
            when(roomTypeRepository.findAll()).thenReturn(List.of(rt1, rt2));

            seeder.run();

            ArgumentCaptor<List<Inventory>> captor = ArgumentCaptor.captor();
            verify(inventoryRepository).saveAll(captor.capture());

            // 2 room types × 365 days = 730
            assertThat(captor.getValue()).hasSize(730);
        }

        @Test
        @DisplayName("inventory entries start from today and span exactly 365 days")
        void run_inventoryEmpty_datesStartTodayFor365Days() throws Exception {
            when(roomTypeRepository.count()).thenReturn(1L);
            when(inventoryRepository.count()).thenReturn(0L);

            RoomType rt = roomTypeWithUnits(10);
            when(roomTypeRepository.findAll()).thenReturn(List.of(rt));

            LocalDate expectedStart = LocalDate.now();
            LocalDate expectedEnd   = expectedStart.plusDays(364);

            seeder.run();

            ArgumentCaptor<List<Inventory>> captor = ArgumentCaptor.captor();
            verify(inventoryRepository).saveAll(captor.capture());

            List<LocalDate> dates = captor.getValue().stream()
                    .map(Inventory::getDate)
                    .toList();

            assertThat(dates).contains(expectedStart);
            assertThat(dates).contains(expectedEnd);
            assertThat(dates).doesNotContain(expectedEnd.plusDays(1));
        }

        @Test
        @DisplayName("each inventory entry is initialised with the room type's totalUnits")
        void run_inventoryEmpty_setsAvailableUnitsFromRoomType() throws Exception {
            when(roomTypeRepository.count()).thenReturn(1L);
            when(inventoryRepository.count()).thenReturn(0L);

            int expectedUnits = 7;
            RoomType rt = roomTypeWithUnits(expectedUnits);
            when(roomTypeRepository.findAll()).thenReturn(List.of(rt));

            seeder.run();

            ArgumentCaptor<List<Inventory>> captor = ArgumentCaptor.captor();
            verify(inventoryRepository).saveAll(captor.capture());

            assertThat(captor.getValue())
                    .allSatisfy(inv -> assertThat(inv.getAvailableUnits()).isEqualTo(expectedUnits));
        }

        @Test
        @DisplayName("each inventory entry references the correct room type")
        void run_inventoryEmpty_linksCorrectRoomType() throws Exception {
            when(roomTypeRepository.count()).thenReturn(1L);
            when(inventoryRepository.count()).thenReturn(0L);

            RoomType rt = roomTypeWithUnits(3);
            when(roomTypeRepository.findAll()).thenReturn(List.of(rt));

            seeder.run();

            ArgumentCaptor<List<Inventory>> captor = ArgumentCaptor.captor();
            verify(inventoryRepository).saveAll(captor.capture());

            assertThat(captor.getValue())
                    .allSatisfy(inv -> assertThat(inv.getRoomType()).isSameAs(rt));
        }

        @Test
        @DisplayName("produces no inventory entries when there are no room types")
        void run_noRoomTypes_savesEmptyInventoryList() throws Exception {
            when(roomTypeRepository.count()).thenReturn(1L);
            when(inventoryRepository.count()).thenReturn(0L);
            when(roomTypeRepository.findAll()).thenReturn(List.of());

            seeder.run();

            ArgumentCaptor<List<Inventory>> captor = ArgumentCaptor.captor();
            verify(inventoryRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Ordering / interaction tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("run() – ordering and interactions")
    class RunOrdering {

        @Test
        @DisplayName("roomTypesSeed runs before inventorySeed (room types saved before inventory is read)")
        void run_roomTypesSeedBeforeInventorySeed() throws Exception {
            // Both tables start empty so both seeds run
            when(roomTypeRepository.count()).thenReturn(0L);
            when(inventoryRepository.count()).thenReturn(0L);

            for (String code : List.of(
                    "BKK-PEN-001", "BKK-MAN-002", "BKK-CAP-003", "BKK-ANA-004",
                    "BKK-CEN-005", "BKK-CHA-006", "BKK-IBI-007", "BKK-NOV-008",
                    "BKK-SOB-009", "BKK-IND-010")) {
                when(hotelRepository.findByCode(code)).thenReturn(Optional.of(hotelWithCode(code)));
            }
            when(roomTypeRepository.findAll()).thenReturn(List.of());

            seeder.run();

            // Both repositories must have been asked to save
            verify(roomTypeRepository).saveAll(anyList());
            verify(inventoryRepository).saveAll(anyList());
        }
    }
}