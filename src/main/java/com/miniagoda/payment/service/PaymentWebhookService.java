package com.miniagoda.payment.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.payment.entity.Payment;
import com.miniagoda.payment.entity.PaymentStatus;
import com.miniagoda.payment.repository.PaymentRepository;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;

@Service
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;

    public PaymentWebhookService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }
    
    @Transactional
    public void handleEvent(Event event) {
        Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();

        if(stripeObject.isEmpty()) return;

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject.get();
                handlePaymentSucceeded(paymentIntent.getId());
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent paymentIntent = (PaymentIntent) stripeObject.get();
                handlePaymentFailed(paymentIntent.getId());
            }
            default -> {}
        }
    }

    private void handlePaymentSucceeded(String stripePaymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
        .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus(PaymentStatus.SUCCESS);
        Booking booking = payment.getBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
    }

    private void handlePaymentFailed(String stripePaymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
        .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus(PaymentStatus.FAILED);
        Booking booking = payment.getBooking();
        booking.setStatus(BookingStatus.CANCELLED);
    }
}