package com.miniagoda.common.seed;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagoda.hotel.dto.RoomTypeSeed;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.hotel.repository.HotelRepository;
import com.miniagoda.hotel.repository.RoomTypeRepository;
import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;

@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {
    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;
    
    public DataSeeder(
                        RoomTypeRepository roomTypeRepository,
                        ObjectMapper objectMapper,
                        HotelRepository hotelRepository,
                        InventoryRepository inventoryRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.hotelRepository = hotelRepository;
        this.objectMapper = objectMapper;
        this.inventoryRepository = inventoryRepository;
    }

    public void run(String... args) throws Exception {
        roomTypesSeed();
        inventorySeed();
    }

    private void inventorySeed() throws Exception {
        if(inventoryRepository.count() > 0) return;

        List<RoomType> roomTypes = roomTypeRepository.findAll();
        LocalDate today = LocalDate.now();
        List<Inventory> inventories = new ArrayList<>();

        for(RoomType roomType : roomTypes) {
            for(int i = 0; i < 365; i++) {
                Inventory inventory = new Inventory();

                inventory.setRoomType(roomType);
                inventory.setAvailableUnits(roomType.getTotalUnits());
                inventory.setDate(today.plusDays(i));

                inventories.add(inventory);
            }
        }
        inventoryRepository.saveAll(inventories);
    }

    private void roomTypesSeed() throws Exception {
        if(roomTypeRepository.count() > 0) return;

        try(InputStream stream = DataSeeder.class.getResourceAsStream("/data/room_types.json")) {
            if (stream == null) throw new RuntimeException("room_types.json not found");
            List<RoomTypeSeed> seeds = objectMapper.readValue(stream, new TypeReference<List<RoomTypeSeed>>() {});

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