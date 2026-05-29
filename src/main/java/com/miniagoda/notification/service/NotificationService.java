package com.miniagoda.notification.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.miniagoda.notification.dto.EmailMessage;
import com.miniagoda.notification.event.AccountRegisteredEvent;
import com.miniagoda.notification.event.BookingCancelledEvent;
import com.miniagoda.notification.event.BookingConfirmedEvent;
import com.miniagoda.notification.event.PaymentFailureEvent;
import com.miniagoda.notification.event.PaymentSuccessEvent;
import com.miniagoda.notification.exception.NotificationException;
import com.miniagoda.notification.gateway.EmailGateway;
import com.miniagoda.notification.template.EmailTemplateRenderer;

@Service
public class NotificationService {

    private final EmailGateway emailGateway;
    private final EmailTemplateRenderer renderer;

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    private static final String BOOKING_CONFIRMED_TEMPLATE = "notification/booking-confirmed";
    private static final String BOOKING_CONFIRMED_SUBJECT = "Your Booking is Confirmed!";

    private static final String BOOKING_CANCELLED_TEMPLATE = "notification/booking-cancelled";
    private static final String BOOKING_CANCELLED_SUBJECT = "Your Booking is Cancelled!";

    private static final String PAYMENT_SUCCESS_TEMPLATE = "notification/payment-success";
    private static final String PAYMENT_SUCCESS_SUBJECT = "Your Payment is Successful!";

    private static final String PAYMENT_FAILURE_TEMPLATE = "notification/payment-failure";
    private static final String PAYMENT_FAILURE_SUBJECT = "Your Payment Could Not Be Processed";

    private static final String ACCOUNT_REGISTER_TEMPLATE = "notification/account-registered";
    private static final String ACCOUNT_REGISTER_SUBJECT = "Welcome to miniAgoda – Please Verify Your Email";

    public NotificationService(EmailGateway emailGateway, EmailTemplateRenderer renderer) {
        this.emailGateway = emailGateway;
        this.renderer = renderer;
    }

    public void sendBookingConfirmed(BookingConfirmedEvent event) {
        
        Map<String, Object> variables = Map.of(
            "bookingId", event.getBookingId(),
            "guestName", event.getGuestName(),
            "guestEmail", event.getGuestEmail(),
            "hotelName", event.getHotelName(),
            "checkIn", event.getCheckIn(),
            "checkOut", event.getCheckOut(),
            "totalAmount", event.getTotalAmount(),
            "currency", event.getCurrency()
        );

        sendEmail(
            event.getGuestEmail(),
            BOOKING_CONFIRMED_SUBJECT,
            BOOKING_CONFIRMED_TEMPLATE,
            variables,
            buildPlainTextBodyForBookingConfirmation(event)
        );
    }

    public void sendBookingCancelled(BookingCancelledEvent event) {
        Map<String, Object> variables = Map.of(
            "bookingId", event.getBookingId(),
            "guestName", event.getGuestName(),
            "guestEmail", event.getGuestEmail(),
            "hotelName", event.getHotelName(),
            "checkIn", event.getCheckIn(),
            "checkOut", event.getCheckOut(),
            "totalAmount", event.getTotalAmount(),
            "currency", event.getCurrency(),
            "reason", event.getCancellationReason()
        );

        sendEmail(
            event.getGuestEmail(),
            BOOKING_CANCELLED_SUBJECT,
            BOOKING_CANCELLED_TEMPLATE,
            variables,
            buildPlainTextBodyForBookingCancellation(event)
        );
    }

    public void sendPaymentSuccess(PaymentSuccessEvent event) {
        Map<String, Object> variables = Map.of(
            "bookingId", event.getBookingId(),
            "payerEmail", event.getPayingUserEmail(),
            "payerName", event.getPayingUserName(),
            "totalAmount", event.getAmount(),
            "currency", event.getCurrency()
        );

        sendEmail(
            event.getPayingUserEmail(),
            PAYMENT_SUCCESS_SUBJECT,
            PAYMENT_SUCCESS_TEMPLATE,
            variables,
            buildPlainTextBodyForPaymentSuccess(event)
        );
    }

    public void sendPaymentFailure(PaymentFailureEvent event) {
        Map<String, Object> variables = Map.of(
            "bookingId", event.getBookingId(),
            "payerEmail", event.getPayingUserEmail(),
            "payerName", event.getPayingUserName(),
            "totalAmount", event.getAmount(),
            "currency", event.getCurrency(),
            "reason", event.getFailureReason()
        );

        sendEmail(
            event.getPayingUserEmail(),
            PAYMENT_FAILURE_SUBJECT,
            PAYMENT_FAILURE_TEMPLATE,
            variables,
            buildPlainTextBodyForPaymentFailure(event)
        );
    }

    public void sendAccountRegistered(AccountRegisteredEvent event) {
        Map<String, Object> variables = Map.of(
            "userEmail", event.getUserEmail(),
            "userName", event.getUserName(),
            "verificationLink", event.getVerificationLink()
        );

        sendEmail(
            event.getUserEmail(),
            ACCOUNT_REGISTER_SUBJECT,
            ACCOUNT_REGISTER_TEMPLATE,
            variables,
            buildPlainTextBodyForAccountRegister(event)
        );
    }

    private void sendEmail(String to, String subject, String template, Map<String, Object> variables, String plainTextBody) {
        String htmlBody = renderer.render(template, variables);
        EmailMessage message = new EmailMessage(to, subject, htmlBody, plainTextBody);
        try {
            emailGateway.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new NotificationException("Sending has failed!", e);
        }
    }

    private String buildPlainTextBodyForBookingConfirmation(BookingConfirmedEvent event) {
        return "Dear " + event.getGuestName() + ",\n\n"
            + "Your booking has been successfully confirmed.\n\n"
            + "Booking ID: " + event.getBookingId() + "\n"
            + "Hotel: " + event.getHotelName() + "\n"
            + "Check-in: " + event.getCheckIn() + "\n"
            + "Check-out: " + event.getCheckOut() + "\n"
            + "Total Amount: " + event.getCurrency() + " " + event.getTotalAmount() + "\n\n"
            + "Thank you for choosing miniAgoda.\n"
            + "The miniAgoda Team";
    }

    private String buildPlainTextBodyForBookingCancellation(BookingCancelledEvent event) {
        return "Dear " + event.getGuestName() + ",\n\n"
            + "Your booking has been successfully cancelled.\n\n"
            + "Booking ID: " + event.getBookingId() + "\n"
            + "Hotel: " + event.getHotelName() + "\n"
            + "Check-in: " + event.getCheckIn() + "\n"
            + "Check-out: " + event.getCheckOut() + "\n"
            + "Total Amount: " + event.getCurrency() + " " + event.getTotalAmount() + "\n\n"
            + "Cancellation Reason: " + event.getCancellationReason() + "\n\n"
            + "Thank you for choosing miniAgoda.\n"
            + "The miniAgoda Team";
    }

    private String buildPlainTextBodyForPaymentSuccess(PaymentSuccessEvent event) {
        return "Dear " + event.getPayingUserName() + ",\n\n"
            + "Your payment has been successfully processed.\n\n"
            + "Booking ID: " + event.getBookingId() + "\n"
            + "Amount Paid: " + event.getCurrency() + " " + event.getAmount() + "\n\n"
            + "Thank you for choosing miniAgoda.\n"
            + "The miniAgoda Team";
    }

    private String buildPlainTextBodyForPaymentFailure(PaymentFailureEvent event) {
        return "Dear " + event.getPayingUserName() + ",\n\n"
            + "Unfortunately, your payment could not be processed.\n\n"
            + "Booking ID: " + event.getBookingId() + "\n"
            + "Amount: " + event.getCurrency() + " " + event.getAmount() + "\n"
            + "Reason: " + event.getFailureReason() + "\n\n"
            + "Please try again or contact support if the issue persists.\n\n"
            + "Thank you for choosing miniAgoda.\n"
            + "The miniAgoda Team";
    }

    private String buildPlainTextBodyForAccountRegister(AccountRegisteredEvent event) {
        return "Dear " + event.getUserName() + ",\n\n"
            + "Your account has been successfully registered.\n\n"
            + "Here is the verification link: " + event.getVerificationLink() + "\n\n"
            + "Thank you for choosing miniAgoda.\n"
            + "The miniAgoda Team";
    }
}