package com.miniagoda.payment.service;

import org.springframework.stereotype.Service;

import com.miniagoda.booking.entity.Booking;
import com.miniagoda.booking.entity.BookingStatus;
import com.miniagoda.booking.service.BookingService;
import com.miniagoda.payment.dto.PaymentGatewayRequest;
import com.miniagoda.payment.dto.PaymentIntentRequest;
import com.miniagoda.payment.dto.PaymentIntentResponse;
import com.miniagoda.payment.dto.RefundGatewayRequest;
import com.miniagoda.payment.dto.RefundRequest;
import com.miniagoda.payment.dto.RefundResponse;
import com.miniagoda.payment.entity.Payment;
import com.miniagoda.payment.entity.PaymentStatus;
import com.miniagoda.payment.gateway.PaymentEvent;
import com.miniagoda.payment.gateway.PaymentGateway;
import com.miniagoda.payment.repository.PaymentRepository;

@Service
public class PaymentService {

    private final BookingService bookingService;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;

    public PaymentService(
        BookingService bookingService, 
        PaymentGateway paymentGateway,
        PaymentRepository paymentRepository
    ) {
        this.bookingService = bookingService;
        this.paymentGateway = paymentGateway;
        this.paymentRepository = paymentRepository;
    }

    public PaymentIntentResponse createPayment(PaymentIntentRequest request) throws Exception {
        Booking booking = bookingService.findById(request.getBookingId());

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

        return response;
    }

    public void handleWebHook(String payload, String sigHeader) throws Exception {
        PaymentEvent event = paymentGateway.parseWebhook(payload, sigHeader);

        switch (event.getType()) {
            case PAYMENT_SUCCEEDED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                payment.setStatus(PaymentStatus.SUCCESS);
                
                Booking booking = payment.getBooking();
                booking.setStatus(BookingStatus.CONFIRMED);
            }
            case PAYMENT_FAILED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                payment.setStatus(PaymentStatus.FAILED);
                
                Booking booking = payment.getBooking();
                booking.setStatus(BookingStatus.CANCELLED);
            }
            case REFUND_SUCCEEDED -> {
                Payment payment = paymentRepository.findByPaymentToken(event.getPaymentId());
                payment.setStatus(PaymentStatus.REFUNDED);
            }
            case UNKNOWN -> {
                throw new RuntimeException("Unknown payment status!!");
            }
        }
    }
}