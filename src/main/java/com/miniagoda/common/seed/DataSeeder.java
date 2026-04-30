package com.miniagoda.common.seed;

import java.io.InputStream;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.repository.HotelRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class DataSeeder implements CommandLineRunner {
    private final HotelRepository hotelRepository;
    private final ObjectMapper mapper;

    DataSeeder(HotelRepository hotelRepository, ObjectMapper mapper) {
        this.hotelRepository = hotelRepository;
        this.mapper = mapper;
    }
    
    @Override
    public void run(String... args) throws Exception {
        if(hotelRepository.count() > 0) return;

        InputStream stream = DataSeeder.class.getResourceAsStream("/data/hotels.json");
        List<Hotel> hotels = mapper.readValue(stream, new TypeReference<List<Hotel>>() {});
        hotelRepository.saveAll(hotels);
    }
}