package com.miniagoda.booking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.dto.BookingResponse;
import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;

import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;

    public BookingService(InventoryRepository inventoryRepository, BookingRepository bookingRepository) {
        this.inventoryRepository = inventoryRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest bookingRequest) {
        LocalDate checkIn = bookingRequest.getCheckIn();
        LocalDate checkOut = bookingRequest.getCheckOut();
        int roomsRequested = bookingRequest.getRoomsRequested();
        
        if(checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
            throw new IllegalArgumentException("Invalid date range");
        }

        if(roomsRequested <= 0) {
            throw new IllegalArgumentException("Requested room should be > 0");
        }

        List<Inventory> inventories = inventoryRepository
        .findByRoomTypeIdAndDateBetween(bookingRequest.getRoomTypeId(), checkIn, checkOut.minusDays(1));

        long expectedDays = ChronoUnit.DAYS.between(checkIn, checkOut);

        if(inventories.size() != expectedDays) {
            throw new RuntimeException("Inventory missing for some dates");
        }

        int minAvailable = Integer.MAX_VALUE;
        for(Inventory inventory : inventories) {
            minAvailable = Math.min(minAvailable, inventory.getAvailableUnits());
        }

        if(minAvailable < roomsRequested) {
            throw new RuntimeException("Not enough rooms available");
        }

        for(Inventory inventory : inventories) {
            inventory.setAvailableUnits(inventory.getAvailableUnits() - roomsRequested);
        }

        inventoryRepository.saveAll(inventories);

        RoomType roomType = inventories.get(0).getRoomType();
        BigDecimal totalPrice = roomType.getPrice()
        .multiply(BigDecimal.valueOf(expectedDays))
        .multiply(BigDecimal.valueOf(roomsRequested));

        Booking booking = new Booking();
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkOut);
        booking.setRoomType(roomType);
        booking.setRoomsBooked(roomsRequested);
        booking.setTotalPrice(totalPrice);
        booking.setExpiredAt(LocalDateTime.now().plusMinutes(15));
        booking.setStatus(BookingStatus.PENDING);

        bookingRepository.save(booking);

        return new BookingResponse(
            booking.getId(),
            roomType.getId(),
            roomType.getName(),
            checkIn,
            checkOut,
            roomsRequested,
            totalPrice,
            booking.getStatus(),
            booking.getExpiredAt()
        );
    }

    public Booking findById(UUID id) {
        return bookingRepository.findById(id).orElseThrow(() -> new RuntimeException("Booking is not found!"));
    }
}