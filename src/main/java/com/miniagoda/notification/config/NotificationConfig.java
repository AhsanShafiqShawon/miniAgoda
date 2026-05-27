package com.miniagoda.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sendgrid.SendGrid;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    private final NotificationProperties properties;

    public NotificationConfig(NotificationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SendGrid sendGridClient() {
        SendGrid sendGrid = new SendGrid(properties.getSendGrid().getApiKey());
        return sendGrid;
    }
}