package com.miniagoda.common.seed;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.hotel.repository.RoomTypeRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DataSeeder implements CommandLineRunner {
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final ObjectMapper mapper;

    DataSeeder(HotelRepository hotelRepository,
        RoomTypeRepository roomTypeRepository,
         ObjectMapper mapper) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.mapper = mapper;
    }
    
    @Override
    public void run(String... args) throws Exception {
        hotelSeed();
        roomTypeSeed();
    }

    private void hotelSeed() throws Exception {
        if(hotelRepository.count() > 0) return;

        try (InputStream stream = DataSeeder.class.getResourceAsStream("/data/hotels.json")) {
            if (stream == null) throw new RuntimeException("hotels.json not found");
            List<Hotel> hotels = mapper.readValue(stream, new TypeReference<List<Hotel>>() {});
            hotelRepository.saveAll(hotels);
        }
    }

    private void roomTypeSeed() throws Exception {
        if(roomTypeRepository.count() > 0) return;

        try (InputStream stream = DataSeeder.class.getResourceAsStream("/data/room_types.json")) {
            if (stream == null) throw new RuntimeException("room_types.json not found");
            List<RoomTypeSeed> seeds = mapper.readValue(stream, new TypeReference<List<RoomTypeSeed>>() {});

            List<RoomType> roomTypes = new ArrayList<>();

            for(RoomTypeSeed seed : seeds) {
                var hotel = hotelRepository.findByCode(seed.getHotelCode()).
                orElseThrow(() -> new RuntimeException("Hotel not found " + seed.getHotelCode()));

                RoomType roomType = new RoomType();
                roomType.setName(seed.getName());
                roomType.setCapacity(seed.getCapacity());
                roomType.setPrice(seed.getPrice());
                roomType.setTotalUnits(seed.getTotalUnits());

                roomType.setHotel(hotel);
                roomTypes.add(roomType);
            }
            roomTypeRepository.saveAll(roomTypes);
        }
    }
}