package com.miniagoda.inventory.service;

import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private UUID roomTypeId;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        roomTypeId = UUID.randomUUID();
        checkIn    = LocalDate.now().plusDays(1);
        checkOut   = LocalDate.now().plusDays(3); // 2-night stay by default
    }

    // ── isAvailable ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Returns true when all nights have enough available units")
        void returnsTrueWhenAllNightsAvailable() {
            // 2-night stay → 2 inventory records required (checkIn to checkOut-1)
            List<Inventory> inventories = List.of(
                    buildInventory(5, checkIn),
                    buildInventory(3, checkIn.plusDays(1))
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(inventories);

            boolean result = inventoryService.isAvailable(roomTypeId, checkIn, checkOut, 2, 2);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when requested rooms exceed the minimum available units across nights")
        void returnsFalseWhenNotEnoughRoomsOnOneNight() {
            // Night 1 has 5 units, Night 2 has only 1 — requesting 2 should fail
            List<Inventory> inventories = List.of(
                    buildInventory(5, checkIn),
                    buildInventory(1, checkIn.plusDays(1))
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(inventories);

            boolean result = inventoryService.isAvailable(roomTypeId, checkIn, checkOut, 2, 2);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Returns false when inventory records count does not match expected nights")
        void returnsFalseWhenInventoryRecordsMissing() {
            // 2-night stay but only 1 record returned → gap in inventory
            List<Inventory> inventories = List.of(
                    buildInventory(5, checkIn)
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(inventories);

            boolean result = inventoryService.isAvailable(roomTypeId, checkIn, checkOut, 2, 1);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Returns false when inventory is empty")
        void returnsFalseWhenInventoryEmpty() {
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(Collections.emptyList());

            boolean result = inventoryService.isAvailable(roomTypeId, checkIn, checkOut, 2, 1);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Returns true when requesting exactly the minimum available units")
        void returnsTrueWhenRequestingExactlyMinAvailableUnits() {
            List<Inventory> inventories = List.of(
                    buildInventory(3, checkIn),
                    buildInventory(3, checkIn.plusDays(1))
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(inventories);

            boolean result = inventoryService.isAvailable(roomTypeId, checkIn, checkOut, 2, 3);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when requesting one more than minimum available units")
        void returnsFalseWhenRequestingOneMoreThanMinAvailableUnits() {
            List<Inventory> inventories = List.of(
                    buildInventory(3, checkIn),
                    buildInventory(3, checkIn.plusDays(1))
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(inventories);

            boolean result = inventoryService.isAvailable(roomTypeId, checkIn, checkOut, 2, 4);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Works correctly for a single-night stay")
        void singleNightStay() {
            LocalDate singleCheckOut = checkIn.plusDays(1);
            List<Inventory> inventories = List.of(
                    buildInventory(2, checkIn)
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, singleCheckOut.minusDays(1)))
                    .thenReturn(inventories);

            boolean result = inventoryService.isAvailable(roomTypeId, checkIn, singleCheckOut, 2, 1);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Queries repository with checkOut minus one day")
        void queriesRepositoryWithCorrectDateRange() {
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            inventoryService.isAvailable(roomTypeId, checkIn, checkOut, 2, 1);

            verify(inventoryRepository).findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1));
        }
    }

    // ── getAvailableRooms ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAvailableRooms")
    class GetAvailableRooms {

        @Test
        @DisplayName("Returns the minimum available units across all nights")
        void returnsMinimumAcrossAllNights() {
            List<Inventory> inventories = List.of(
                    buildInventory(10, checkIn),
                    buildInventory(4,  checkIn.plusDays(1))
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(inventories);

            int result = inventoryService.getAvailableRooms(roomTypeId, checkIn, checkOut);

            assertThat(result).isEqualTo(4);
        }

        @Test
        @DisplayName("Returns the single value when there is only one inventory record")
        void returnsSingleValueForOneNight() {
            LocalDate singleCheckOut = checkIn.plusDays(1);
            List<Inventory> inventories = List.of(
                    buildInventory(7, checkIn)
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, singleCheckOut.minusDays(1)))
                    .thenReturn(inventories);

            int result = inventoryService.getAvailableRooms(roomTypeId, checkIn, singleCheckOut);

            assertThat(result).isEqualTo(7);
        }

        @Test
        @DisplayName("Returns Integer.MAX_VALUE when inventory list is empty")
        void returnsMaxIntWhenNoInventory() {
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(Collections.emptyList());

            int result = inventoryService.getAvailableRooms(roomTypeId, checkIn, checkOut);

            // The loop doesn't execute, so the initial Integer.MAX_VALUE is returned.
            // This documents a known edge case in the current implementation.
            assertThat(result).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("Returns minimum correctly when first night is the bottleneck")
        void returnsMinWhenFirstNightIsBottleneck() {
            List<Inventory> inventories = List.of(
                    buildInventory(1, checkIn),
                    buildInventory(9, checkIn.plusDays(1))
            );
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1)))
                    .thenReturn(inventories);

            int result = inventoryService.getAvailableRooms(roomTypeId, checkIn, checkOut);

            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("Queries repository with checkOut minus one day")
        void queriesRepositoryWithCorrectDateRange() {
            when(inventoryRepository.findByRoomTypeIdAndDateBetween(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            inventoryService.getAvailableRooms(roomTypeId, checkIn, checkOut);

            verify(inventoryRepository).findByRoomTypeIdAndDateBetween(
                    roomTypeId, checkIn, checkOut.minusDays(1));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Inventory buildInventory(int availableUnits, LocalDate date) {
        Inventory inventory = new Inventory();
        inventory.setAvailableUnits(availableUnits);
        inventory.setDate(date);
        return inventory;
    }
}