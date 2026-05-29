package com.miniagoda.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class AccountRegisteredEvent {
    String userEmail;
    String userName;
    String verificationLink;
}