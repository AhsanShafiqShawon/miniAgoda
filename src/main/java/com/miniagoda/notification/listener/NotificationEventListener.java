package com.miniagoda.notification.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.miniagoda.notification.event.AccountRegisteredNotificationEvent;
import com.miniagoda.notification.event.BookingCancelledNotificationEvent;
import com.miniagoda.notification.event.BookingConfirmedNotificationEvent;
import com.miniagoda.notification.event.PaymentFailureNotificationEvent;
import com.miniagoda.notification.event.PaymentSuccessNotificationEvent;
import com.miniagoda.notification.service.NotificationService;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);


    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("notificationExecutor")
    public void handleBookingConfirmed(BookingConfirmedNotificationEvent event) {
        long start = System.currentTimeMillis();
        notificationService.sendBookingConfirmed(event.getEvent());
        log.info("[ASYNC] Email sent in {} ms on thread: {}", System.currentTimeMillis() - start, Thread.currentThread().getName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("notificationExecutor")
    public void handleBookingCancelled(BookingCancelledNotificationEvent event) {
        notificationService.sendBookingCancelled(event.getEvent());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("notificationExecutor")
    public void handlePaymentSuccess(PaymentSuccessNotificationEvent event) {
        notificationService.sendPaymentSuccess(event.getEvent());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("notificationExecutor")
    public void handlePaymentFailure(PaymentFailureNotificationEvent event) {
        notificationService.sendPaymentFailure(event.getEvent());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("notificationExecutor")
    public void handleAccountRegistered(AccountRegisteredNotificationEvent event) {
        notificationService.sendAccountRegistered(event.getEvent());
    }
}