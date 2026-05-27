package com.miniagoda.payment.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.booking.service.BookingService;
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

    public PaymentService(
        BookingService bookingService, 
        PaymentGateway paymentGateway,
        PaymentRepository paymentRepository,
        BookingRepository bookingRepository
    ) {
        this.bookingService = bookingService;
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
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

    public void handleWebHook(String payload, String sigHeader) throws Exception {
        PaymentEvent event = paymentGateway.parseWebhook(payload, sigHeader);
        
        switch (event.getType()) {
            case PAYMENT_SUCCEEDED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                Booking booking = payment.getBooking();

                payment.setStatus(PaymentStatus.SUCCESS);
                booking.setStatus(BookingStatus.CONFIRMED);

                paymentRepository.save(payment);
                bookingRepository.save(booking);
            }
            case PAYMENT_FAILED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                Booking booking = payment.getBooking();

                payment.setStatus(PaymentStatus.FAILED);
                booking.setStatus(BookingStatus.CANCELLED);

                paymentRepository.save(payment);
                bookingRepository.save(booking);
            }
            case REFUND_SUCCEEDED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                payment.setStatus(PaymentStatus.REFUNDED);

                paymentRepository.save(payment);
            }
            case UNKNOWN -> {}
        }
    }
}