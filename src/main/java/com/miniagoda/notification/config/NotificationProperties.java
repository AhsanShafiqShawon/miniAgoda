package com.miniagoda.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {
    private String fromEmail;
    private String fromName;

    private SendGrid sendGrid = new SendGrid();

    @Data
    public static class SendGrid {
        private String apiKey;
    }
}