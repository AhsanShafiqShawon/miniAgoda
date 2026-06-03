package com.miniagoda.payment.service;

import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.booking.service.BookingService;
import com.miniagoda.hotel.entity.Hotel;
import com.miniagoda.hotel.entity.RoomType;
import com.miniagoda.notification.event.*;
import com.miniagoda.payment.dto.*;
import com.miniagoda.payment.entity.Payment;
import com.miniagoda.payment.entity.PaymentStatus;
import com.miniagoda.payment.exception.PaymentAlreadyExistException;
import com.miniagoda.payment.gateway.PaymentEvent;
import com.miniagoda.payment.gateway.PaymentGateway;
import com.miniagoda.payment.repository.PaymentRepository;
import com.miniagoda.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private BookingService bookingService;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private UUID bookingId;
    private UUID paymentId;

    private User user;
    private Hotel hotel;
    private RoomType roomType;
    private Booking booking;
    private Payment payment;

    @BeforeEach
    void setUp() throws Exception {
        bookingId = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        user = buildUser("Alice", "Smith", "alice@example.com");
        hotel = buildHotel("Grand Hotel");
        roomType = buildRoomType(hotel);
        booking = buildBooking(bookingId, user, roomType);
        payment = buildPayment(paymentId, booking, "tok_abc123");

        // Set up an authenticated user matching the booking owner by default
        setAuthenticatedUser("alice@example.com");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── createPayment ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPayment")
    class CreatePayment {

        private PaymentIntentRequest request;
        private PaymentIntentResponse gatewayResponse;

        @BeforeEach
        void setUp() {
            request = new PaymentIntentRequest(bookingId);
            gatewayResponse = new PaymentIntentResponse(
                    bookingId, "tok_abc123", new BigDecimal("500.00"), "USD");
        }

        @Test
        @DisplayName("Returns PaymentIntentResponse from the gateway on success")
        void returnsGatewayResponse() throws Exception {
            when(bookingService.findById(bookingId)).thenReturn(booking);
            when(paymentRepository.existsByBooking(booking)).thenReturn(false);
            when(paymentGateway.createPayment(any())).thenReturn(gatewayResponse);

            PaymentIntentResponse result = paymentService.createPayment(request);

            assertThat(result).isEqualTo(gatewayResponse);
        }

        @Test
        @DisplayName("Saves a PENDING payment with correct fields")
        void savesPendingPaymentWithCorrectFields() throws Exception {
            when(bookingService.findById(bookingId)).thenReturn(booking);
            when(paymentRepository.existsByBooking(booking)).thenReturn(false);
            when(paymentGateway.createPayment(any())).thenReturn(gatewayResponse);

            paymentService.createPayment(request);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());

            Payment saved = captor.getValue();
            assertThat(saved.getAmount()).isEqualByComparingTo(booking.getTotalPrice());
            assertThat(saved.getCurrency()).isEqualTo(booking.getCurrency());
            assertThat(saved.getPaymentToken()).isEqualTo("tok_abc123");
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(saved.getBooking()).isEqualTo(booking);
        }

        @Test
        @DisplayName("Throws PaymentAlreadyExistException when payment already exists for booking")
        void throwsWhenPaymentAlreadyExists() throws Exception {
            when(bookingService.findById(bookingId)).thenReturn(booking);
            when(paymentRepository.existsByBooking(booking)).thenReturn(true);

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(PaymentAlreadyExistException.class)
                    .hasMessage("Payment already exists for this booking");

            verify(paymentGateway, never()).createPayment(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws AccessDeniedException when authenticated user does not own the booking")
        void throwsWhenUserDoesNotOwnBooking() throws Exception {
            setAuthenticatedUser("other@example.com");
            when(bookingService.findById(bookingId)).thenReturn(booking);
            when(paymentRepository.existsByBooking(booking)).thenReturn(false);

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You do not own this booking");

            verify(paymentGateway, never()).createPayment(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Passes correct gateway request with booking amount and currency")
        void passesCorrectGatewayRequest() throws Exception {
            when(bookingService.findById(bookingId)).thenReturn(booking);
            when(paymentRepository.existsByBooking(booking)).thenReturn(false);
            when(paymentGateway.createPayment(any())).thenReturn(gatewayResponse);

            paymentService.createPayment(request);

            ArgumentCaptor<PaymentGatewayRequest> captor =
                    ArgumentCaptor.forClass(PaymentGatewayRequest.class);
            verify(paymentGateway).createPayment(captor.capture());

            PaymentGatewayRequest gwr = captor.getValue();
            assertThat(gwr.getBookingId()).isEqualTo(booking.getId());
            assertThat(gwr.getAmount()).isEqualByComparingTo(booking.getTotalPrice());
            assertThat(gwr.getCurrency()).isEqualTo(booking.getCurrency());
        }
    }

    // ── refund ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refund")
    class Refund {

        private RefundRequest request;
        private RefundResponse gatewayResponse;

        @BeforeEach
        void setUp() {
            request = new RefundRequest(paymentId, "Guest request");
            gatewayResponse = new RefundResponse(paymentId, new BigDecimal("500.00"), "USD");
        }

        @Test
        @DisplayName("Returns RefundResponse from the gateway on success")
        void returnsGatewayResponse() throws Exception {
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentGateway.refund(any())).thenReturn(gatewayResponse);

            RefundResponse result = paymentService.refund(request);

            assertThat(result).isEqualTo(gatewayResponse);
        }

        @Test
        @DisplayName("Updates payment status to REFUNDED and saves it")
        void updatesPaymentStatusToRefunded() throws Exception {
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentGateway.refund(any())).thenReturn(gatewayResponse);

            paymentService.refund(request);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("Passes correct gateway request with payment token and reason")
        void passesCorrectGatewayRequest() throws Exception {
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentGateway.refund(any())).thenReturn(gatewayResponse);

            paymentService.refund(request);

            ArgumentCaptor<RefundGatewayRequest> captor =
                    ArgumentCaptor.forClass(RefundGatewayRequest.class);
            verify(paymentGateway).refund(captor.capture());

            RefundGatewayRequest rgr = captor.getValue();
            assertThat(rgr.getPaymentId()).isEqualTo(paymentId);
            assertThat(rgr.getPaymentToken()).isEqualTo("tok_abc123");
            assertThat(rgr.getAmount()).isEqualByComparingTo(payment.getAmount());
            assertThat(rgr.getCurrency()).isEqualTo(payment.getCurrency());
            assertThat(rgr.getReason()).isEqualTo("Guest request");
        }

        @Test
        @DisplayName("Throws RuntimeException when payment is not found")
        void throwsWhenPaymentNotFound() throws Exception {
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.refund(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment not found!");

            verify(paymentGateway, never()).refund(any());
        }
    }

    // ── handleWebHook ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebHook")
    class HandleWebHook {

        private static final String PAYLOAD    = "{\"type\":\"payment_intent.succeeded\"}";
        private static final String SIG_HEADER = "sig_abc123";
        private static final String TOKEN      = "tok_abc123";

        // ── PAYMENT_SUCCEEDED ────────────────────────────────────────────────

        @Nested
        @DisplayName("PAYMENT_SUCCEEDED")
        class PaymentSucceeded {

            @BeforeEach
            void setUp() throws Exception {
                PaymentEvent event = PaymentEvent.builder()
                        .type(PaymentEvent.Type.PAYMENT_SUCCEEDED)
                        .paymentId(TOKEN)
                        .build();
                when(paymentGateway.parseWebhook(PAYLOAD, SIG_HEADER)).thenReturn(event);
                when(paymentRepository.findByPaymentToken(TOKEN)).thenReturn(payment);
            }

            @Test
            @DisplayName("Sets payment status to SUCCESS and booking status to CONFIRMED")
            void updatesPaymentAndBookingStatus() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            }

            @Test
            @DisplayName("Saves updated payment and booking")
            void savesPaymentAndBooking() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                verify(paymentRepository).save(payment);
                verify(bookingRepository).save(booking);
            }

            @Test
            @DisplayName("Publishes BookingConfirmedNotificationEvent with correct guest details")
            void publishesBookingConfirmedEvent() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
                verify(applicationEventPublisher, times(2)).publishEvent(captor.capture());

                BookingConfirmedNotificationEvent notificationEvent = captor.getAllValues().stream()
                        .filter(e -> e instanceof BookingConfirmedNotificationEvent)
                        .map(e -> (BookingConfirmedNotificationEvent) e)
                        .findFirst().orElseThrow();

                BookingConfirmedEvent inner = notificationEvent.getEvent();
                assertThat(inner.getGuestEmail()).isEqualTo("alice@example.com");
                assertThat(inner.getGuestName()).isEqualTo("Alice Smith");
                assertThat(inner.getHotelName()).isEqualTo("Grand Hotel");
                assertThat(inner.getCheckIn()).isEqualTo(booking.getCheckIn());
                assertThat(inner.getCheckOut()).isEqualTo(booking.getCheckOut());
                assertThat(inner.getTotalAmount()).isEqualByComparingTo(booking.getTotalPrice());
                assertThat(inner.getCurrency()).isEqualTo(booking.getCurrency());
            }

            @Test
            @DisplayName("Publishes PaymentSuccessNotificationEvent with correct payer details")
            void publishesPaymentSuccessEvent() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
                verify(applicationEventPublisher, times(2)).publishEvent(captor.capture());

                PaymentSuccessNotificationEvent notificationEvent = captor.getAllValues().stream()
                        .filter(e -> e instanceof PaymentSuccessNotificationEvent)
                        .map(e -> (PaymentSuccessNotificationEvent) e)
                        .findFirst().orElseThrow();

                PaymentSuccessEvent inner = notificationEvent.getEvent();
                assertThat(inner.getPayingUserEmail()).isEqualTo("alice@example.com");
                assertThat(inner.getPayingUserName()).isEqualTo("Alice Smith");
                assertThat(inner.getAmount()).isEqualByComparingTo(booking.getTotalPrice());
                assertThat(inner.getCurrency()).isEqualTo(booking.getCurrency());
                assertThat(inner.getBookingId()).isEqualTo(booking.getId());
            }

            @Test
            @DisplayName("Does nothing when no payment is found for the token")
            void doesNothingWhenPaymentNotFound() throws Exception {
                when(paymentRepository.findByPaymentToken(TOKEN)).thenReturn(null);

                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                verify(paymentRepository, never()).save(any());
                verify(bookingRepository, never()).save(any());
                verify(applicationEventPublisher, never()).publishEvent(any());
            }
        }

        // ── PAYMENT_FAILED ───────────────────────────────────────────────────

        @Nested
        @DisplayName("PAYMENT_FAILED")
        class PaymentFailed {

            @BeforeEach
            void setUp() throws Exception {
                PaymentEvent event = PaymentEvent.builder()
                        .type(PaymentEvent.Type.PAYMENT_FAILED)
                        .paymentId(TOKEN)
                        .failureMessage("Card declined")
                        .build();
                when(paymentGateway.parseWebhook(PAYLOAD, SIG_HEADER)).thenReturn(event);
                when(paymentRepository.findByPaymentToken(TOKEN)).thenReturn(payment);
            }

            @Test
            @DisplayName("Sets payment status to FAILED and booking status to CANCELLED")
            void updatesPaymentAndBookingStatus() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
                assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            }

            @Test
            @DisplayName("Saves updated payment and booking")
            void savesPaymentAndBooking() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                verify(paymentRepository).save(payment);
                verify(bookingRepository).save(booking);
            }

            @Test
            @DisplayName("Publishes BookingCancelledNotificationEvent with failure reason")
            void publishesBookingCancelledEvent() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
                verify(applicationEventPublisher, times(2)).publishEvent(captor.capture());

                BookingCancelledNotificationEvent notificationEvent = captor.getAllValues().stream()
                        .filter(e -> e instanceof BookingCancelledNotificationEvent)
                        .map(e -> (BookingCancelledNotificationEvent) e)
                        .findFirst().orElseThrow();

                BookingCancelledEvent inner = notificationEvent.getEvent();
                assertThat(inner.getGuestEmail()).isEqualTo("alice@example.com");
                assertThat(inner.getGuestName()).isEqualTo("Alice Smith");
                assertThat(inner.getHotelName()).isEqualTo("Grand Hotel");
                assertThat(inner.getCancellationReason()).isEqualTo("Card declined");
            }

            @Test
            @DisplayName("Publishes PaymentFailureNotificationEvent with failure reason")
            void publishesPaymentFailureEvent() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
                verify(applicationEventPublisher, times(2)).publishEvent(captor.capture());

                PaymentFailureNotificationEvent notificationEvent = captor.getAllValues().stream()
                        .filter(e -> e instanceof PaymentFailureNotificationEvent)
                        .map(e -> (PaymentFailureNotificationEvent) e)
                        .findFirst().orElseThrow();

                PaymentFailureEvent inner = notificationEvent.getEvent();
                assertThat(inner.getPayingUserEmail()).isEqualTo("alice@example.com");
                assertThat(inner.getPayingUserName()).isEqualTo("Alice Smith");
                assertThat(inner.getFailureReason()).isEqualTo("Card declined");
            }
        }

        // ── REFUND_SUCCEEDED ─────────────────────────────────────────────────

        @Nested
        @DisplayName("REFUND_SUCCEEDED")
        class RefundSucceeded {

            @BeforeEach
            void setUp() throws Exception {
                PaymentEvent event = PaymentEvent.builder()
                        .type(PaymentEvent.Type.REFUND_SUCCEEDED)
                        .paymentId(TOKEN)
                        .build();
                when(paymentGateway.parseWebhook(PAYLOAD, SIG_HEADER)).thenReturn(event);
                when(paymentRepository.findByPaymentToken(TOKEN)).thenReturn(payment);
            }

            @Test
            @DisplayName("Sets payment status to REFUNDED and saves it")
            void updatesPaymentStatusToRefunded() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
                verify(paymentRepository).save(payment);
            }

            @Test
            @DisplayName("Does not touch the booking or publish any events")
            void doesNotTouchBookingOrPublishEvents() throws Exception {
                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                verify(bookingRepository, never()).save(any());
                verify(applicationEventPublisher, never()).publishEvent(any());
            }
        }

        // ── UNKNOWN ──────────────────────────────────────────────────────────

        @Nested
        @DisplayName("UNKNOWN")
        class Unknown {

            @Test
            @DisplayName("Does not save or publish anything for unknown event type")
            void doesNothingForUnknownEvent() throws Exception {
                PaymentEvent event = PaymentEvent.builder()
                        .type(PaymentEvent.Type.UNKNOWN)
                        .paymentId(TOKEN)
                        .build();
                when(paymentGateway.parseWebhook(PAYLOAD, SIG_HEADER)).thenReturn(event);

                paymentService.handleWebHook(PAYLOAD, SIG_HEADER);

                verify(paymentRepository, never()).save(any());
                verify(bookingRepository, never()).save(any());
                verify(applicationEventPublisher, never()).publishEvent(any());
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setAuthenticatedUser(String email) {
        var auth = new UsernamePasswordAuthenticationToken(email, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User buildUser(String firstName, String lastName, String email) throws Exception {
        User u = new User();
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(email);
        u.setPassword("hashed");
        return u;
    }

    private Hotel buildHotel(String name) throws Exception {
        Hotel h = new Hotel();
        h.setName(name);
        h.setCity("Bangkok");
        h.setAddress("1 Main St");
        h.setCode("HTL-001");
        return h;
    }

    private RoomType buildRoomType(Hotel hotel) throws Exception {
        RoomType rt = new RoomType();
        rt.setName("Standard");
        rt.setCapacity(2);
        rt.setTotalUnits(10);
        rt.setPrice(new BigDecimal("500.00"));
        rt.setHotel(hotel);
        return rt;
    }

    private Booking buildBooking(UUID id, User user, RoomType roomType) throws Exception {
        Booking b = new Booking();
        b.setCheckIn(LocalDate.now().plusDays(5));
        b.setCheckOut(LocalDate.now().plusDays(8));
        b.setRoomsBooked(1);
        b.setTotalPrice(new BigDecimal("500.00"));
        b.setCurrency("USD");
        b.setExpiredAt(LocalDateTime.now().plusMinutes(15));
        b.setStatus(BookingStatus.PENDING);
        b.setUser(user);
        b.setRoomType(roomType);
        injectId(b, id);
        return b;
    }

    private Payment buildPayment(UUID id, Booking booking, String token) throws Exception {
        Payment p = new Payment();
        p.setAmount(booking.getTotalPrice());
        p.setCurrency(booking.getCurrency());
        p.setPaymentToken(token);
        p.setStatus(PaymentStatus.PENDING);
        p.setBooking(booking);
        injectId(p, id);
        return p;
    }

    private void injectId(Object entity, UUID id) throws Exception {
        var field = com.miniagoda.common.entity.BaseEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}