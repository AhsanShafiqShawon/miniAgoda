package com.miniagoda.notification.gateway;

import com.miniagoda.notification.dto.EmailMessage;

public interface EmailGateway {

    void send(EmailMessage message);
}