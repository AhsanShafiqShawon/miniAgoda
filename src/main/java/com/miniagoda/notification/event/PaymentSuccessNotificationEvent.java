package com.miniagoda.notification.event;

import org.springframework.context.ApplicationEvent;

public class PaymentSuccessNotificationEvent extends ApplicationEvent {
    private final PaymentSuccessEvent event;

    public PaymentSuccessNotificationEvent(Object source, PaymentSuccessEvent event) {
        super(source);
        this.event = event;
    }

    public PaymentSuccessEvent getEvent() {
        return event;
    }
}