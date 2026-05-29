package com.miniagoda.notification.event;

import org.springframework.context.ApplicationEvent;

public class BookingConfirmedNotificationEvent extends ApplicationEvent {
    private final BookingConfirmedEvent event;

    public BookingConfirmedNotificationEvent(Object source, BookingConfirmedEvent event) {
        super(source);
        this.event = event;
    }

    public BookingConfirmedEvent getEvent() {
        return event;
    }
}