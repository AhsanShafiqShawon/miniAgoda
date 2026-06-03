package com.miniagoda.notification.service;

import com.miniagoda.notification.dto.EmailMessage;
import com.miniagoda.notification.event.*;
import com.miniagoda.notification.exception.NotificationException;
import com.miniagoda.notification.gateway.EmailGateway;
import com.miniagoda.notification.template.EmailTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmailGateway emailGateway;

    @Mock
    private EmailTemplateRenderer renderer;

    @InjectMocks
    private NotificationService notificationService;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private static final String RENDERED_HTML = "<html>rendered</html>";

    private UUID bookingId;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        checkIn   = LocalDate.now().plusDays(5);
        checkOut  = LocalDate.now().plusDays(8);

        // Default: renderer always returns a predictable HTML string
        when(renderer.render(anyString(), anyMap())).thenReturn(RENDERED_HTML);
    }

    // ── sendBookingConfirmed ─────────────────────────────────────────────────

    @Nested
    @DisplayName("sendBookingConfirmed")
    class SendBookingConfirmed {

        private BookingConfirmedEvent event;

        @BeforeEach
        void setUp() {
            event = BookingConfirmedEvent.builder()
                    .bookingId(bookingId)
                    .guestName("Alice Smith")
                    .guestEmail("alice@example.com")
                    .hotelName("Grand Hotel")
                    .checkIn(checkIn)
                    .checkOut(checkOut)
                    .totalAmount(new BigDecimal("500.00"))
                    .currency("USD")
                    .build();
        }

        @Test
        @DisplayName("Sends email to the guest's address with the correct subject")
        void sendsEmailToGuestWithCorrectSubject() throws Exception {
            notificationService.sendBookingConfirmed(event);

            EmailMessage sent = captureEmailMessage();
            assertThat(sent.getTo()).isEqualTo("alice@example.com");
            assertThat(sent.getSubject()).isEqualTo("Your Booking is Confirmed!");
        }

        @Test
        @DisplayName("Renders the correct template with all required variables")
        void rendersCorrectTemplateWithVariables() {
            notificationService.sendBookingConfirmed(event);

            verify(renderer).render(eq("notification/BookingConfirmed"), argThat(vars ->
                    vars.get("bookingId").equals(bookingId) &&
                    vars.get("guestName").equals("Alice Smith") &&
                    vars.get("guestEmail").equals("alice@example.com") &&
                    vars.get("hotelName").equals("Grand Hotel") &&
                    vars.get("checkIn").equals(checkIn) &&
                    vars.get("checkOut").equals(checkOut) &&
                    vars.get("totalAmount").equals(new BigDecimal("500.00")) &&
                    vars.get("currency").equals("USD")
            ));
        }

        @Test
        @DisplayName("Email htmlBody is the rendered template output")
        void emailHtmlBodyIsRenderedOutput() throws Exception {
            notificationService.sendBookingConfirmed(event);

            assertThat(captureEmailMessage().getHtmlBody()).isEqualTo(RENDERED_HTML);
        }

        @Test
        @DisplayName("Plain-text body contains key booking details")
        void plainTextBodyContainsBookingDetails() throws Exception {
            notificationService.sendBookingConfirmed(event);

            String plain = captureEmailMessage().getPlainTextBody();
            assertThat(plain)
                    .contains("Alice Smith")
                    .contains(bookingId.toString())
                    .contains("Grand Hotel")
                    .contains(checkIn.toString())
                    .contains(checkOut.toString())
                    .contains("USD")
                    .contains("500.00");
        }

        @Test
        @DisplayName("Throws NotificationException when gateway fails")
        void throwsNotificationExceptionOnGatewayFailure() throws Exception {
            doThrow(new RuntimeException("SMTP error")).when(emailGateway).send(any());

            assertThatThrownBy(() -> notificationService.sendBookingConfirmed(event))
                    .isInstanceOf(NotificationException.class)
                    .hasMessage("Sending has failed!")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    // ── sendBookingCancelled ─────────────────────────────────────────────────

    @Nested
    @DisplayName("sendBookingCancelled")
    class SendBookingCancelled {

        private BookingCancelledEvent event;

        @BeforeEach
        void setUp() {
            event = BookingCancelledEvent.builder()
                    .bookingId(bookingId)
                    .guestName("Bob Jones")
                    .guestEmail("bob@example.com")
                    .hotelName("Sea View Resort")
                    .checkIn(checkIn)
                    .checkOut(checkOut)
                    .totalAmount(new BigDecimal("300.00"))
                    .currency("THB")
                    .cancellationReason("Guest request")
                    .build();
        }

        @Test
        @DisplayName("Sends email to the guest's address with the correct subject")
        void sendsEmailToGuestWithCorrectSubject() throws Exception {
            notificationService.sendBookingCancelled(event);

            EmailMessage sent = captureEmailMessage();
            assertThat(sent.getTo()).isEqualTo("bob@example.com");
            assertThat(sent.getSubject()).isEqualTo("Your Booking is Cancelled!");
        }

        @Test
        @DisplayName("Renders the correct template with all required variables including reason")
        void rendersCorrectTemplateWithVariables() {
            notificationService.sendBookingCancelled(event);

            verify(renderer).render(eq("notification/BookingCancelled"), argThat(vars ->
                    vars.get("bookingId").equals(bookingId) &&
                    vars.get("guestName").equals("Bob Jones") &&
                    vars.get("reason").equals("Guest request")
            ));
        }

        @Test
        @DisplayName("Plain-text body contains cancellation reason")
        void plainTextBodyContainsCancellationReason() throws Exception {
            notificationService.sendBookingCancelled(event);

            assertThat(captureEmailMessage().getPlainTextBody())
                    .contains("Bob Jones")
                    .contains(bookingId.toString())
                    .contains("Guest request");
        }

        @Test
        @DisplayName("Throws NotificationException when gateway fails")
        void throwsNotificationExceptionOnGatewayFailure() throws Exception {
            doThrow(new RuntimeException("SMTP error")).when(emailGateway).send(any());

            assertThatThrownBy(() -> notificationService.sendBookingCancelled(event))
                    .isInstanceOf(NotificationException.class)
                    .hasMessage("Sending has failed!")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    // ── sendPaymentSuccess ───────────────────────────────────────────────────

    @Nested
    @DisplayName("sendPaymentSuccess")
    class SendPaymentSuccess {

        private PaymentSuccessEvent event;

        @BeforeEach
        void setUp() {
            event = PaymentSuccessEvent.builder()
                    .bookingId(bookingId)
                    .payingUserEmail("carol@example.com")
                    .payingUserName("Carol White")
                    .amount(new BigDecimal("250.00"))
                    .currency("USD")
                    .build();
        }

        @Test
        @DisplayName("Sends email to the payer's address with the correct subject")
        void sendsEmailToPayerWithCorrectSubject() throws Exception {
            notificationService.sendPaymentSuccess(event);

            EmailMessage sent = captureEmailMessage();
            assertThat(sent.getTo()).isEqualTo("carol@example.com");
            assertThat(sent.getSubject()).isEqualTo("Your Payment is Successful!");
        }

        @Test
        @DisplayName("Renders the correct template with all required variables")
        void rendersCorrectTemplateWithVariables() {
            notificationService.sendPaymentSuccess(event);

            verify(renderer).render(eq("notification/PaymentSuccess"), argThat(vars ->
                    vars.get("bookingId").equals(bookingId) &&
                    vars.get("payerEmail").equals("carol@example.com") &&
                    vars.get("payerName").equals("Carol White") &&
                    vars.get("totalAmount").equals(new BigDecimal("250.00")) &&
                    vars.get("currency").equals("USD")
            ));
        }

        @Test
        @DisplayName("Plain-text body contains payment details")
        void plainTextBodyContainsPaymentDetails() throws Exception {
            notificationService.sendPaymentSuccess(event);

            assertThat(captureEmailMessage().getPlainTextBody())
                    .contains("Carol White")
                    .contains(bookingId.toString())
                    .contains("250.00")
                    .contains("USD");
        }

        @Test
        @DisplayName("Throws NotificationException when gateway fails")
        void throwsNotificationExceptionOnGatewayFailure() throws Exception {
            doThrow(new RuntimeException("SMTP error")).when(emailGateway).send(any());

            assertThatThrownBy(() -> notificationService.sendPaymentSuccess(event))
                    .isInstanceOf(NotificationException.class)
                    .hasMessage("Sending has failed!")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    // ── sendPaymentFailure ───────────────────────────────────────────────────

    @Nested
    @DisplayName("sendPaymentFailure")
    class SendPaymentFailure {

        private PaymentFailureEvent event;

        @BeforeEach
        void setUp() {
            event = PaymentFailureEvent.builder()
                    .bookingId(bookingId)
                    .payingUserEmail("dave@example.com")
                    .payingUserName("Dave Brown")
                    .amount(new BigDecimal("150.00"))
                    .currency("EUR")
                    .failureReason("Insufficient funds")
                    .build();
        }

        @Test
        @DisplayName("Sends email to the payer's address with the correct subject")
        void sendsEmailToPayerWithCorrectSubject() throws Exception {
            notificationService.sendPaymentFailure(event);

            EmailMessage sent = captureEmailMessage();
            assertThat(sent.getTo()).isEqualTo("dave@example.com");
            assertThat(sent.getSubject()).isEqualTo("Your Payment Could Not Be Processed");
        }

        @Test
        @DisplayName("Renders the correct template with all required variables including failure reason")
        void rendersCorrectTemplateWithVariables() {
            notificationService.sendPaymentFailure(event);

            verify(renderer).render(eq("notification/PaymentFailure"), argThat(vars ->
                    vars.get("bookingId").equals(bookingId) &&
                    vars.get("payerEmail").equals("dave@example.com") &&
                    vars.get("payerName").equals("Dave Brown") &&
                    vars.get("reason").equals("Insufficient funds")
            ));
        }

        @Test
        @DisplayName("Plain-text body contains failure reason")
        void plainTextBodyContainsFailureReason() throws Exception {
            notificationService.sendPaymentFailure(event);

            assertThat(captureEmailMessage().getPlainTextBody())
                    .contains("Dave Brown")
                    .contains(bookingId.toString())
                    .contains("Insufficient funds")
                    .contains("150.00")
                    .contains("EUR");
        }

        @Test
        @DisplayName("Throws NotificationException when gateway fails")
        void throwsNotificationExceptionOnGatewayFailure() throws Exception {
            doThrow(new RuntimeException("SMTP error")).when(emailGateway).send(any());

            assertThatThrownBy(() -> notificationService.sendPaymentFailure(event))
                    .isInstanceOf(NotificationException.class)
                    .hasMessage("Sending has failed!")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    // ── sendAccountRegistered ────────────────────────────────────────────────

    @Nested
    @DisplayName("sendAccountRegistered")
    class SendAccountRegistered {

        private AccountRegisteredEvent event;

        @BeforeEach
        void setUp() {
            event = AccountRegisteredEvent.builder()
                    .userEmail("eve@example.com")
                    .userName("Eve Davis")
                    .verificationLink("https://miniagoda.com/verify?token=abc123")
                    .build();
        }

        @Test
        @DisplayName("Sends email to the user's address with the correct subject")
        void sendsEmailToUserWithCorrectSubject() throws Exception {
            notificationService.sendAccountRegistered(event);

            EmailMessage sent = captureEmailMessage();
            assertThat(sent.getTo()).isEqualTo("eve@example.com");
            assertThat(sent.getSubject()).isEqualTo("Welcome to miniAgoda – Please Verify Your Email");
        }

        @Test
        @DisplayName("Renders the correct template with all required variables")
        void rendersCorrectTemplateWithVariables() {
            notificationService.sendAccountRegistered(event);

            verify(renderer).render(eq("notification/AccountRegistered"), argThat(vars ->
                    vars.get("userEmail").equals("eve@example.com") &&
                    vars.get("userName").equals("Eve Davis") &&
                    vars.get("verificationLink").equals("https://miniagoda.com/verify?token=abc123")
            ));
        }

        @Test
        @DisplayName("Plain-text body contains verification link")
        void plainTextBodyContainsVerificationLink() throws Exception {
            notificationService.sendAccountRegistered(event);

            assertThat(captureEmailMessage().getPlainTextBody())
                    .contains("Eve Davis")
                    .contains("https://miniagoda.com/verify?token=abc123");
        }

        @Test
        @DisplayName("Throws NotificationException when gateway fails")
        void throwsNotificationExceptionOnGatewayFailure() throws Exception {
            doThrow(new RuntimeException("SMTP error")).when(emailGateway).send(any());

            assertThatThrownBy(() -> notificationService.sendAccountRegistered(event))
                    .isInstanceOf(NotificationException.class)
                    .hasMessage("Sending has failed!")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    // ── Cross-cutting: gateway interaction ───────────────────────────────────

    @Nested
    @DisplayName("Gateway interaction")
    class GatewayInteraction {

        @Test
        @DisplayName("Gateway is called exactly once per notification send")
        void gatewayCalledExactlyOnce() throws Exception {
            notificationService.sendAccountRegistered(AccountRegisteredEvent.builder()
                    .userEmail("x@example.com").userName("X").verificationLink("https://link").build());

            verify(emailGateway, times(1)).send(any(EmailMessage.class));
        }

        @Test
        @DisplayName("Renderer is called exactly once per notification send")
        void rendererCalledExactlyOnce() {
            notificationService.sendPaymentSuccess(PaymentSuccessEvent.builder()
                    .bookingId(bookingId).payingUserEmail("x@example.com")
                    .payingUserName("X").amount(BigDecimal.TEN).currency("USD").build());

            verify(renderer, times(1)).render(anyString(), anyMap());
        }

        @Test
        @DisplayName("NotificationException wraps the original gateway cause")
        void notificationExceptionWrapsCause() throws Exception {
            RuntimeException cause = new RuntimeException("Connection refused");
            doThrow(cause).when(emailGateway).send(any());

            assertThatThrownBy(() -> notificationService.sendAccountRegistered(
                    AccountRegisteredEvent.builder()
                            .userEmail("x@example.com").userName("X").verificationLink("https://link").build()))
                    .isInstanceOf(NotificationException.class)
                    .cause()
                    .isSameAs(cause);
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Captures the single {@link EmailMessage} passed to {@code emailGateway.send()}.
     */
    private EmailMessage captureEmailMessage() throws Exception {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway).send(captor.capture());
        return captor.getValue();
    }
}