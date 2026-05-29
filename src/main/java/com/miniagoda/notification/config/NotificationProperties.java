package com.miniagoda.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {
    
    @NotBlank(message = "fromEmail is required")
    private String fromEmail;

    @NotBlank(message = "fromName is required")
    private String fromName;

    private SendGridProperties sendGrid = new SendGridProperties();

    @Data
    public static class SendGridProperties {
        private String apiKey;
    }
}