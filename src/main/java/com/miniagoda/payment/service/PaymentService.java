package com.miniagoda.payment.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.booking.service.BookingService;
import com.miniagoda.notification.event.BookingCancelledEvent;
import com.miniagoda.notification.event.BookingCancelledNotificationEvent;
import com.miniagoda.notification.event.BookingConfirmedEvent;
import com.miniagoda.notification.event.BookingConfirmedNotificationEvent;
import com.miniagoda.notification.event.PaymentFailureEvent;
import com.miniagoda.notification.event.PaymentFailureNotificationEvent;
import com.miniagoda.notification.event.PaymentSuccessEvent;
import com.miniagoda.notification.event.PaymentSuccessNotificationEvent;
import com.miniagoda.payment.dto.PaymentGatewayRequest;
import com.miniagoda.payment.dto.PaymentIntentRequest;
import com.miniagoda.payment.dto.PaymentIntentResponse;
import com.miniagoda.payment.dto.RefundGatewayRequest;
import com.miniagoda.payment.dto.RefundRequest;
import com.miniagoda.payment.dto.RefundResponse;
import com.miniagoda.payment.entity.Payment;
import com.miniagoda.payment.entity.PaymentStatus;
import com.miniagoda.payment.exception.PaymentAlreadyExistException;
import com.miniagoda.payment.gateway.PaymentEvent;
import com.miniagoda.payment.gateway.PaymentGateway;
import com.miniagoda.payment.repository.PaymentRepository;

@Service
public class PaymentService {

    private final BookingService bookingService;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public PaymentService(
        BookingService bookingService, 
        PaymentGateway paymentGateway,
        PaymentRepository paymentRepository,
        BookingRepository bookingRepository,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.bookingService = bookingService;
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public PaymentIntentResponse createPayment(PaymentIntentRequest request) throws Exception {
        Booking booking = bookingService.findById(request.getBookingId());

        if(paymentRepository.existsByBooking(booking)) {
            throw new PaymentAlreadyExistException();
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!booking.getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("You do not own this booking");
        }

        PaymentGatewayRequest gatewayRequest = new PaymentGatewayRequest(
            booking.getId(),
            booking.getTotalPrice(),
            booking.getCurrency()
        );

        PaymentIntentResponse response = paymentGateway.createPayment(gatewayRequest);

        Payment payment = new Payment();
        payment.setAmount(booking.getTotalPrice());
        payment.setCurrency(booking.getCurrency());
        payment.setPaymentToken(response.getPaymentToken());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setBooking(booking);

        paymentRepository.save(payment);        
        
        return response;
    }

    public RefundResponse refund(RefundRequest request) throws Exception {
        Payment payment = paymentRepository.findById(request.getPaymentId())
        .orElseThrow(() -> new RuntimeException("Payment not found!"));

        RefundGatewayRequest gatewayRequest = new RefundGatewayRequest(
            request.getPaymentId(),
            payment.getPaymentToken(),
            payment.getAmount(),
            payment.getCurrency(),
            request.getReason()
        );

        RefundResponse response = paymentGateway.refund(gatewayRequest);
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        return response;
    }

    @Transactional
    public void handleWebHook(String payload, String sigHeader) throws Exception {
        PaymentEvent event = paymentGateway.parseWebhook(payload, sigHeader);
        
        switch (event.getType()) {
            case PAYMENT_SUCCEEDED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                if (payment == null) {
                    log.warn("No payment found for token: {}", event.getPaymentId());
                    return;
                }
                Booking booking = payment.getBooking();

                payment.setStatus(PaymentStatus.SUCCESS);
                booking.setStatus(BookingStatus.CONFIRMED);

                paymentRepository.save(payment);
                bookingRepository.save(booking);

                BookingConfirmedEvent bookingConfirmedEvent = new BookingConfirmedEvent(
                    booking.getId(),
                    booking.getUser().getFirstName() + " " + booking.getUser().getLastName(),
                    booking.getUser().getEmail(),
                    booking.getRoomType().getHotel().getName(),
                    booking.getCheckIn(),
                    booking.getCheckOut(),
                    booking.getTotalPrice(),
                    booking.getCurrency()
                );

                PaymentSuccessEvent paymentSuccessEvent = new PaymentSuccessEvent(
                    booking.getUser().getEmail(),
                    booking.getUser().getFirstName() + " " + booking.getUser().getLastName(),
                    booking.getTotalPrice(),
                    booking.getCurrency(),
                    booking.getId()
                );

                applicationEventPublisher
                .publishEvent(new BookingConfirmedNotificationEvent(this, bookingConfirmedEvent));

                applicationEventPublisher
                .publishEvent(new PaymentSuccessNotificationEvent(this, paymentSuccessEvent));
            }
            case PAYMENT_FAILED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                Booking booking = payment.getBooking();

                payment.setStatus(PaymentStatus.FAILED);
                booking.setStatus(BookingStatus.CANCELLED);

                paymentRepository.save(payment);
                bookingRepository.save(booking);

                BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                    booking.getId(),
                    booking.getUser().getFirstName() + " " + booking.getUser().getLastName(),
                    booking.getUser().getEmail(),
                    booking.getRoomType().getHotel().getName(),
                    booking.getCheckIn(),
                    booking.getCheckOut(),
                    booking.getTotalPrice(),
                    booking.getCurrency(),
                    event.getFailureMessage()
                );

                PaymentFailureEvent paymentFailureEvent = new PaymentFailureEvent(
                    booking.getUser().getEmail(),
                    booking.getUser().getFirstName() + " " + booking.getUser().getLastName(),
                    booking.getTotalPrice(),
                    booking.getCurrency(),
                    booking.getId(),
                    event.getFailureMessage()
                );

                applicationEventPublisher
                .publishEvent(new BookingCancelledNotificationEvent(this, bookingCancelledEvent));

                applicationEventPublisher
                .publishEvent(new PaymentFailureNotificationEvent(this, paymentFailureEvent));
            }
            case REFUND_SUCCEEDED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                payment.setStatus(PaymentStatus.REFUNDED);

                paymentRepository.save(payment);
            }
            case UNKNOWN -> log.warn("Received unknown webhook event type: {}", payload);
        }
    }
}