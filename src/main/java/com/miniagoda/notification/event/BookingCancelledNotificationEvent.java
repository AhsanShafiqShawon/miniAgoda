package com.miniagoda.notification.event;

import org.springframework.context.ApplicationEvent;

public class BookingCancelledNotificationEvent extends ApplicationEvent {
    private final BookingCancelledEvent event;

    public BookingCancelledNotificationEvent(Object source, BookingCancelledEvent event) {
        super(source);
        this.event = event;
    }

    public BookingCancelledEvent getEvent() {
        return event;
    }
}