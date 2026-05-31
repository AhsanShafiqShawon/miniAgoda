package com.miniagoda.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.inventory.service.InventoryService;
import com.miniagoda.search.dto.SearchRequest;
import com.miniagoda.search.dto.SearchResponse;

@ExtendWith(MockitoExtension.class)
public class SearchServiceTest {

    @Mock
    HotelRepository hotelRepository;

    @Mock
    InventoryService inventoryService;

    @InjectMocks
    SearchService searchService;

    @Test
    void getSearchResult_shouldReturnHotelList_whenResultExists() {
        
        // Arrange
        RoomType roomType = createRoomType(2, BigDecimal.valueOf(2500));
        Hotel hotel = createHotel("Bangkok", List.of(roomType));
        List<Hotel> hotels = new ArrayList<>();
        hotels.add(hotel);

        SearchRequest searchRequest = createSearchRequest("Bangkok");

        List<SearchResponse> finalResult = new ArrayList<>();
        SearchResponse searchResponse = new SearchResponse(
            hotel.getId(),
            hotel.getName(),
            hotel.getAddress(),
            hotel.getRating(),
            BigDecimal.valueOf(2500)
        );
        finalResult.add(searchResponse);

        when(hotelRepository.findByCityIgnoreCase("Bangkok"))
        .thenReturn(hotels);

        when(inventoryService.isAvailable(
            roomType.getId(),
            searchRequest.getCheckIn(),
            searchRequest.getCheckOut(),
            searchRequest.getGuests(),
            searchRequest.getRooms()))
        .thenReturn(true);

        // Act
        List<SearchResponse> result = searchService.search(searchRequest);

        // Assert
        assertEquals(finalResult, result);
    }

    @Test
    void getSearchResult_shouldReturnEmptyList_whenNoResultExists() {
        // Arrange
        List<Hotel> hotels = new ArrayList<>();
        
        SearchRequest searchRequest = createSearchRequest("Bangkok");

        when(hotelRepository.findByCityIgnoreCase("Bangkok"))
        .thenReturn(hotels);

        // Act
        List<SearchResponse> result = searchService.search(searchRequest);

        // Assert
        assertEquals(0, result.size());
    }

    private Hotel createHotel(String city, List<RoomType> roomTypes) {
        Hotel hotel = new Hotel();
        hotel.setName("Hotel1");
        hotel.setCity(city);
        hotel.setAddress("Address1");
        hotel.setCode("Code1");
        hotel.setRating(BigDecimal.valueOf(8.5));
        hotel.setRoomTypes(roomTypes);
        return hotel;
    }

    private RoomType createRoomType(int capacity, BigDecimal price) {
        RoomType roomType = new RoomType();
        roomType.setName("Standard");
        roomType.setCapacity(capacity);
        roomType.setPrice(price);
        roomType.setTotalUnits(10);
        return roomType;
    }

    private SearchRequest createSearchRequest(String destination) {
        return new SearchRequest(
            destination,
            LocalDate.of(2026, 6, 20),
            LocalDate.of(2026, 6, 22),
            2,
            1
        );
    }
}