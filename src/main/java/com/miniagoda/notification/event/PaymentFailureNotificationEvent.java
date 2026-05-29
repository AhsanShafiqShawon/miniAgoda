package com.miniagoda.notification.event;

import org.springframework.context.ApplicationEvent;

public class PaymentFailureNotificationEvent extends ApplicationEvent {
    private final PaymentFailureEvent event;

    public PaymentFailureNotificationEvent(Object source, PaymentFailureEvent event) {
        super(source);
        this.event = event;
    }

    public PaymentFailureEvent getEvent() {
        return event;
    }
}