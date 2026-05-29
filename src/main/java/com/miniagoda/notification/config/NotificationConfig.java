package com.miniagoda.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

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
        String apiKey = properties.getSendGrid().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("SendGrid API key is not configured");
        }
        return new SendGrid(apiKey);
    }
}