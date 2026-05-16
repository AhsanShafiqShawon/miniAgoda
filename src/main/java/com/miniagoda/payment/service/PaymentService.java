package com.miniagoda.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.repository.BookingRepository;
import com.miniagoda.payment.dto.PaymentInitiateRequest;
import com.miniagoda.payment.dto.PaymentInitiateResponse;
import com.miniagoda.payment.entity.Payment;
import com.miniagoda.payment.entity.PaymentStatus;
import com.miniagoda.payment.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;


@Service
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final StripeService stripeService;
    private final PaymentRepository paymentRepository;

    public PaymentService(
        BookingRepository bookingRepository,
        StripeService stripeService,
        PaymentRepository paymentRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.stripeService = stripeService;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request) {
        UUID bookingId = request.getBookingId();

        Booking booking = bookingRepository.findById(bookingId)
        .orElseThrow(() -> new RuntimeException("Booking not found"));

        if(booking.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Booking has expired");
        }

        long amount = booking.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntent paymentIntent;
        try {
            paymentIntent = stripeService.createPaymentIntent(amount, "thb");
        } catch(StripeException e) {
            throw new RuntimeException("Failed to create payment intent", e);
        }

        Payment payment = new Payment();
        payment.setAmount(booking.getTotalPrice());
        payment.setCurrency("thb");
        payment.setStripePaymentIntentId(paymentIntent.getId());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setBooking(booking);

        paymentRepository.save(payment);

        return new PaymentInitiateResponse(
            bookingId,
            paymentIntent.getClientSecret(),
            booking.getTotalPrice(),
            "thb"
        );
    }
}