package com.miniagoda.notification.event;

import org.springframework.context.ApplicationEvent;

public class AccountRegisteredNotificationEvent extends ApplicationEvent {
    private final AccountRegisteredEvent event;

    public AccountRegisteredNotificationEvent(Object source, AccountRegisteredEvent event) {
        super(source);
        this.event = event;
    }

    public AccountRegisteredEvent getEvent() {
        return event;
    }
}