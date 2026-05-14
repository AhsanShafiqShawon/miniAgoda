package com.miniagoda.hotel.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.miniagoda.hotel.dto.HotelDetailRequest;
import com.miniagoda.hotel.dto.HotelDetailResponse;
import com.miniagoda.hotel.dto.RoomTypeResponse;
import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.inventory.service.InventoryService;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final InventoryService inventoryService;

    public HotelService(HotelRepository hotelRepository, InventoryService inventoryService) {
        this.hotelRepository = hotelRepository;
        this.inventoryService = inventoryService;
    }

    public HotelDetailResponse getHotelDetail(HotelDetailRequest hotelDetailRequest) {
        Hotel hotel = hotelRepository.findById(hotelDetailRequest.getHotelId())
                .orElseThrow(() -> new RuntimeException("Hotel not found!"));

        List<RoomTypeResponse> roomTypeResponses = new ArrayList<>();
        List<RoomType> roomTypes = hotel.getRoomTypes();

        for (RoomType roomType : roomTypes) {
            boolean available = inventoryService.isAvailable(
                    roomType.getId(),
                    hotelDetailRequest.getCheckIn(),
                    hotelDetailRequest.getCheckOut(),
                    hotelDetailRequest.getGuests(),
                    hotelDetailRequest.getRooms()
            );

            if (available) {
                int availableRooms = inventoryService.getAvailableRooms(
                        roomType.getId(),
                        hotelDetailRequest.getCheckIn(),
                        hotelDetailRequest.getCheckOut()
                );
                roomTypeResponses.add(new RoomTypeResponse(
                        roomType.getId(),
                        roomType.getName(),
                        roomType.getCapacity(),
                        roomType.getPrice(),
                        availableRooms
                ));
            }
        }

        return new HotelDetailResponse(
                hotel.getId(),
                hotel.getName(),
                hotel.getAddress(),
                hotel.getRating(),
                roomTypeResponses
        );
    }
}