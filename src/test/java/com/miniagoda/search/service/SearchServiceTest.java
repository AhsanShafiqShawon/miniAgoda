package com.miniagoda.search.service;

import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.inventory.service.InventoryService;
import com.miniagoda.search.dto.SearchRequest;
import com.miniagoda.search.dto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private HotelRepository hotelRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private SearchService searchService;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private static final LocalDate CHECK_IN  = LocalDate.now().plusDays(1);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(4);

    private Hotel hotel;
    private RoomType standardRoom;   // capacity 2, price $100
    private RoomType suiteRoom;      // capacity 4, price $300
    private SearchRequest request;

    @BeforeEach
    void setUp() throws Exception {
        hotel = buildHotel("Grand Hotel", "Bangkok", "1 Main St", new BigDecimal("4.5"));

        standardRoom = buildRoomType(hotel, "Standard", 2, new BigDecimal("100.00"));
        suiteRoom    = buildRoomType(hotel, "Suite",    4, new BigDecimal("300.00"));

        hotel.setRoomTypes(List.of(standardRoom, suiteRoom));

        request = SearchRequest.builder()
                .destination("Bangkok")
                .checkIn(CHECK_IN)
                .checkOut(CHECK_OUT)
                .guests(2)
                .rooms(1)
                .build();
    }

    // ── search ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("search")
    class Search {

        @Nested
        @DisplayName("No hotels in destination")
        class NoHotels {

            @Test
            @DisplayName("Returns empty list when no hotels found for destination")
            void returnsEmptyListWhenNoHotels() {
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of());

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("Does not call inventory service when no hotels found")
            void doesNotCallInventoryWhenNoHotels() {
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of());

                searchService.search(request);

                verifyNoInteractions(inventoryService);
            }
        }

        @Nested
        @DisplayName("Availability filtering")
        class AvailabilityFiltering {

            @Test
            @DisplayName("Returns hotel when at least one room type is available")
            void returnsHotelWhenRoomAvailable() {
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(standardRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getHotelId()).isEqualTo(hotel.getId());
            }

            @Test
            @DisplayName("Excludes hotel when no room types are available")
            void excludesHotelWhenNoRoomsAvailable() {
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(any(), any(), any(), anyInt(), anyInt()))
                        .thenReturn(false);

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("Excludes room types whose capacity is less than requested guests")
            void excludesRoomTypesBelowCapacity() {
                // request 4 guests — only suiteRoom (capacity 4) qualifies, not standardRoom (capacity 2)
                request = SearchRequest.builder()
                        .destination("Bangkok")
                        .checkIn(CHECK_IN)
                        .checkOut(CHECK_OUT)
                        .guests(4)
                        .rooms(1)
                        .build();

                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(suiteRoom.getId(), CHECK_IN, CHECK_OUT, 4, 1))
                        .thenReturn(true);

                searchService.search(request);

                verify(inventoryService, never())
                        .isAvailable(eq(standardRoom.getId()), any(), any(), anyInt(), anyInt());
                verify(inventoryService)
                        .isAvailable(suiteRoom.getId(), CHECK_IN, CHECK_OUT, 4, 1);
            }

            @Test
            @DisplayName("Does not call inventory for room types below capacity")
            void doesNotCallInventoryForUndersizedRooms() {
                // request 3 guests — standardRoom (capacity 2) should be skipped entirely
                request = SearchRequest.builder()
                        .destination("Bangkok")
                        .checkIn(CHECK_IN)
                        .checkOut(CHECK_OUT)
                        .guests(3)
                        .rooms(1)
                        .build();

                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(suiteRoom.getId(), CHECK_IN, CHECK_OUT, 3, 1))
                        .thenReturn(false);

                searchService.search(request);

                verify(inventoryService, never())
                        .isAvailable(eq(standardRoom.getId()), any(), any(), anyInt(), anyInt());
            }
        }

        @Nested
        @DisplayName("Minimum price calculation")
        class MinPrice {

            @Test
            @DisplayName("Returns minimum price across all available room types")
            void returnsMinPriceAcrossAvailableRooms() {
                // both rooms available for guests=2 — min price should be $100 (standardRoom)
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(standardRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);
                when(inventoryService.isAvailable(suiteRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
            }

            @Test
            @DisplayName("Returns price of the only available room type when one is unavailable")
            void returnsPriceOfOnlyAvailableRoom() {
                // standardRoom unavailable, suiteRoom available — price should be $300
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(standardRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(false);
                when(inventoryService.isAvailable(suiteRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("300.00"));
            }
        }

        @Nested
        @DisplayName("Response fields")
        class ResponseFields {

            @Test
            @DisplayName("Response contains correct hotel details")
            void responseContainsCorrectHotelDetails() {
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(standardRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);

                List<SearchResponse> result = searchService.search(request);

                SearchResponse response = result.get(0);
                assertThat(response.getHotelId()).isEqualTo(hotel.getId());
                assertThat(response.getHotelName()).isEqualTo("Grand Hotel");
                assertThat(response.getHotelAddress()).isEqualTo("1 Main St");
                assertThat(response.getRating()).isEqualByComparingTo(new BigDecimal("4.5"));
            }
        }

        @Nested
        @DisplayName("Multiple hotels")
        class MultipleHotels {

            @Test
            @DisplayName("Returns only hotels with at least one available room")
            void returnsOnlyHotelsWithAvailableRooms() throws Exception {
                Hotel hotelB = buildHotel("Budget Inn", "Bangkok", "2 Side St", new BigDecimal("3.0"));
                RoomType budgetRoom = buildRoomType(hotelB, "Basic", 2, new BigDecimal("50.00"));
                hotelB.setRoomTypes(List.of(budgetRoom));

                when(hotelRepository.findByCityIgnoreCase("Bangkok"))
                        .thenReturn(List.of(hotel, hotelB));

                // hotel's standardRoom available; hotelB unavailable
                when(inventoryService.isAvailable(standardRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);
                when(inventoryService.isAvailable(suiteRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(false);
                when(inventoryService.isAvailable(budgetRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(false);

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getHotelId()).isEqualTo(hotel.getId());
            }

            @Test
            @DisplayName("Returns all hotels when all have available rooms")
            void returnsAllHotelsWhenAllAvailable() throws Exception {
                Hotel hotelB = buildHotel("Budget Inn", "Bangkok", "2 Side St", new BigDecimal("3.0"));
                RoomType budgetRoom = buildRoomType(hotelB, "Basic", 2, new BigDecimal("50.00"));
                hotelB.setRoomTypes(List.of(budgetRoom));

                when(hotelRepository.findByCityIgnoreCase("Bangkok"))
                        .thenReturn(List.of(hotel, hotelB));

                when(inventoryService.isAvailable(standardRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);
                when(inventoryService.isAvailable(suiteRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(false);
                when(inventoryService.isAvailable(budgetRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).hasSize(2);
            }

            @Test
            @DisplayName("Searches case-insensitively by destination")
            void searchesCaseInsensitively() {
                request = SearchRequest.builder()
                        .destination("bangkok")
                        .checkIn(CHECK_IN)
                        .checkOut(CHECK_OUT)
                        .guests(2)
                        .rooms(1)
                        .build();

                when(hotelRepository.findByCityIgnoreCase("bangkok")).thenReturn(List.of(hotel));
                when(inventoryService.isAvailable(standardRoom.getId(), CHECK_IN, CHECK_OUT, 2, 1))
                        .thenReturn(true);

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).hasSize(1);
                verify(hotelRepository).findByCityIgnoreCase("bangkok");
            }
        }

        @Nested
        @DisplayName("Hotel with no room types")
        class EmptyRoomTypes {

            @Test
            @DisplayName("Excludes hotel with empty room types list")
            void excludesHotelWithNoRoomTypes() {
                hotel.setRoomTypes(List.of());
                when(hotelRepository.findByCityIgnoreCase("Bangkok")).thenReturn(List.of(hotel));

                List<SearchResponse> result = searchService.search(request);

                assertThat(result).isEmpty();
                verifyNoInteractions(inventoryService);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Hotel buildHotel(String name, String city, String address, BigDecimal rating) throws Exception {
        Hotel h = new Hotel();
        h.setName(name);
        h.setCity(city);
        h.setAddress(address);
        h.setRating(rating);
        h.setCode(name.replaceAll("\\s+", "-").toUpperCase());
        injectId(h);
        return h;
    }

    private RoomType buildRoomType(Hotel hotel, String name, int capacity, BigDecimal price) throws Exception {
        RoomType rt = new RoomType();
        rt.setName(name);
        rt.setCapacity(capacity);
        rt.setTotalUnits(10);
        rt.setPrice(price);
        rt.setHotel(hotel);
        injectId(rt);
        return rt;
    }

    private void injectId(Object entity) throws Exception {
        Field field = com.miniagoda.common.entity.BaseEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, UUID.randomUUID());
    }
}