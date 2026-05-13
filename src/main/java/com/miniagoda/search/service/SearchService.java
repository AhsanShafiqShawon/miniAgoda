package com.miniagoda.search.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.inventory.service.InventoryService;
import com.miniagoda.search.dto.SearchRequest;
import com.miniagoda.search.dto.SearchResponse;

@Service
public class SearchService {
    private final HotelRepository hotelRepository;
    private final InventoryService inventoryService;

    public SearchService(HotelRepository hotelRepository, InventoryService inventoryService) {
        this.hotelRepository = hotelRepository;
        this.inventoryService = inventoryService;
    }

    public List<SearchResponse> search(SearchRequest searchRequest) {
        List<SearchResponse> result = new ArrayList<>();
        
        List<Hotel> hotels = hotelRepository.findByCityIgnoreCase(searchRequest.getDestination());
        for(Hotel hotel : hotels) {
            List<RoomType> roomTypes = hotel.getRoomTypes();
            boolean hasAvailableRoom = false;
            BigDecimal minPrice = null;
            for(RoomType roomType : roomTypes) {
                if(inventoryService.isAvailable(
                    searchRequest.getCheckIn(),
                    searchRequest.getCheckOut(),
                    searchRequest.getGuests(),
                    searchRequest.getRooms())) {
                    minPrice = (minPrice == null) ? roomType.getPrice() : minPrice.min(roomType.getPrice());
                    hasAvailableRoom = true;
                }
            }
            if(hasAvailableRoom) {
                result.add(new SearchResponse(hotel.getName(),
                hotel.getAddress(),
                hotel.getRating(),
                minPrice));
            }
        }
        return result;
    }
}