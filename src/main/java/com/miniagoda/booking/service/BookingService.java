package com.miniagoda.booking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.dto.BookingResponse;
import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;
import com.miniagoda.user.entity.User;
import com.miniagoda.user.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public BookingService(
        InventoryRepository inventoryRepository,
        BookingRepository bookingRepository,
        UserRepository userRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest bookingRequest) {
        LocalDate checkIn = bookingRequest.getCheckIn();
        LocalDate checkOut = bookingRequest.getCheckOut();
        int roomsRequested = bookingRequest.getRoomsRequested();
        
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

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        booking.setUser(user);
        booking.setCurrency("THB");

        bookingRepository.save(booking);

        return toResponse(booking);
    }

    public Booking findById(UUID id) {
        return bookingRepository.findById(id).orElseThrow(() -> new RuntimeException("Booking is not found!"));
    }

    public List<BookingResponse> getBookings() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        List<BookingResponse> responses = new ArrayList<BookingResponse>();
        List<Booking> bookings = bookingRepository.findByUserId(user.getId());

        for(Booking booking : bookings) responses.add(toResponse(booking));

        return responses;
    }

    private BookingResponse toResponse(Booking booking) {
        BookingResponse bookingResponse = new BookingResponse(
            booking.getId(),
            booking.getRoomType().getId(),
            booking.getRoomType().getName(),
            booking.getCheckIn(),
            booking.getCheckOut(),
            booking.getRoomsBooked(),
            booking.getTotalPrice(),
            booking.getStatus(),
            booking.getExpiredAt()
        );
        return bookingResponse;
    }
}