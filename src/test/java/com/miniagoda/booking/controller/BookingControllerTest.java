package com.miniagoda.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.miniagoda.auth.security.JwtAuthFilter;
import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.dto.BookingResponse;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.service.BookingService;
import com.miniagoda.common.config.SecurityConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.mockito.Mockito;

@WebMvcTest(
    controllers = BookingController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = { JwtAuthFilter.class, SecurityConfig.class }
    )
)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private ObjectMapper objectMapper;

    // Shared test data
    private UUID roomTypeId;
    private UUID bookingId;
    private LocalDate today;
    private LocalDate tomorrow;
    private LocalDate dayAfterTomorrow;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(bookingService);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        roomTypeId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        today = LocalDate.now();
        tomorrow = today.plusDays(1);
        dayAfterTomorrow = today.plusDays(2);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private BookingRequest validRequest() {
        return new BookingRequest(roomTypeId, today, tomorrow, 2);
    }

    private BookingResponse sampleResponse() {
        return new BookingResponse(
                bookingId,
                roomTypeId,
                "Deluxe Room",
                today,
                tomorrow,
                2,
                BigDecimal.valueOf(3000),
                BookingStatus.PENDING,
                LocalDateTime.now().plusMinutes(15)
        );
    }

    // ===========================================================================
    // POST /api/v1/booking
    // ===========================================================================

    @Nested
    @DisplayName("POST /api/v1/booking")
    class CreateBooking {

        @Test
        @DisplayName("returns 201 with booking response on valid request")
        void createBooking_validRequest_returns201() throws Exception {
            BookingRequest request = validRequest();
            BookingResponse response = sampleResponse();

            when(bookingService.createBooking(any(BookingRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                    .andExpect(jsonPath("$.roomTypeId").value(roomTypeId.toString()))
                    .andExpect(jsonPath("$.roomTypeName").value("Deluxe Room"))
                    .andExpect(jsonPath("$.roomsBooked").value(2))
                    .andExpect(jsonPath("$.totalPrice").value(3000))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(bookingService, times(1)).createBooking(any(BookingRequest.class));
        }

        @Test
        @DisplayName("returns 400 when roomTypeId is null")
        void createBooking_nullRoomTypeId_returns400() throws Exception {
            BookingRequest request = new BookingRequest(null, today, tomorrow, 2);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("returns 400 when checkIn date is null")
        void createBooking_nullCheckIn_returns400() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, null, tomorrow, 2);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("returns 400 when checkOut date is null")
        void createBooking_nullCheckOut_returns400() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, today, null, 2);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("returns 400 when checkIn is in the past")
        void createBooking_pastCheckIn_returns400() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, today.minusDays(1), tomorrow, 2);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("returns 400 when checkOut is today (not future)")
        void createBooking_checkOutToday_returns400() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, today, today, 2);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("returns 400 when checkOut is before checkIn")
        void createBooking_checkOutBeforeCheckIn_returns400() throws Exception {
            LocalDate checkIn = today.plusDays(2);
            LocalDate checkOut = today.plusDays(1); // before checkIn
            BookingRequest invalidOrder = new BookingRequest(roomTypeId, checkIn, checkOut, 2);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidOrder)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("returns 400 when roomsRequested is 0 (below minimum)")
        void createBooking_zeroRooms_returns400() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, today, tomorrow, 0);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("returns 400 when roomsRequested exceeds 30")
        void createBooking_tooManyRooms_returns400() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, today, tomorrow, 31);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }

        @Test
        @DisplayName("accepts exactly 1 room (minimum boundary)")
        void createBooking_oneRoom_returns201() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, today, tomorrow, 1);
            BookingResponse response = new BookingResponse(
                    bookingId, roomTypeId, "Deluxe Room", today, tomorrow,
                    1, BigDecimal.valueOf(1500), BookingStatus.PENDING, LocalDateTime.now().plusMinutes(15)
            );

            when(bookingService.createBooking(any())).thenReturn(response);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roomsBooked").value(1));
        }

        @Test
        @DisplayName("accepts exactly 30 rooms (maximum boundary)")
        void createBooking_thirtyRooms_returns201() throws Exception {
            BookingRequest request = new BookingRequest(roomTypeId, today, tomorrow, 30);
            BookingResponse response = new BookingResponse(
                    bookingId, roomTypeId, "Deluxe Room", today, tomorrow,
                    30, BigDecimal.valueOf(45000), BookingStatus.PENDING, LocalDateTime.now().plusMinutes(15)
            );

            when(bookingService.createBooking(any())).thenReturn(response);

            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roomsBooked").value(30));
        }

        @Test
        @DisplayName("returns 400 when body is missing")
        void createBooking_missingBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/booking")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(bookingService);
        }
    }

    // ===========================================================================
    // GET /api/v1/bookings
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/bookings")
    class GetBookings {

        @Test
        @DisplayName("returns 200 with list of bookings")
        void getBookings_returnsListOf200() throws Exception {
            BookingResponse r1 = sampleResponse();
            BookingResponse r2 = new BookingResponse(
                    UUID.randomUUID(), roomTypeId, "Standard Room",
                    tomorrow, dayAfterTomorrow, 1, BigDecimal.valueOf(1000),
                    BookingStatus.PENDING, LocalDateTime.now().plusMinutes(15)
            );

            when(bookingService.getBookings()).thenReturn(List.of(r1, r2));

            mockMvc.perform(get("/api/v1/bookings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].bookingId").value(r1.getBookingId().toString()))
                    .andExpect(jsonPath("$[0].roomTypeName").value("Deluxe Room"))
                    .andExpect(jsonPath("$[1].roomTypeName").value("Standard Room"));

            verify(bookingService, times(1)).getBookings();
        }

        @Test
        @DisplayName("returns 200 with empty list when user has no bookings")
        void getBookings_noBookings_returnsEmptyList() throws Exception {
            when(bookingService.getBookings()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/bookings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("returns booking with all expected fields")
        void getBookings_responseContainsAllFields() throws Exception {
            BookingResponse response = sampleResponse();
            when(bookingService.getBookings()).thenReturn(List.of(response));

            mockMvc.perform(get("/api/v1/bookings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].bookingId").exists())
                    .andExpect(jsonPath("$[0].roomTypeId").exists())
                    .andExpect(jsonPath("$[0].roomTypeName").exists())
                    .andExpect(jsonPath("$[0].checkIn").exists())
                    .andExpect(jsonPath("$[0].checkOut").exists())
                    .andExpect(jsonPath("$[0].roomsBooked").exists())
                    .andExpect(jsonPath("$[0].totalPrice").exists())
                    .andExpect(jsonPath("$[0].status").exists())
                    .andExpect(jsonPath("$[0].expiredAt").exists());
        }
    }
}