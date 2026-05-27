package com.miniagoda.notification.gateway.sendGrid;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.miniagoda.notification.config.NotificationProperties;
import com.miniagoda.notification.dto.EmailMessage;
import com.miniagoda.notification.gateway.EmailGateway;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;

@Service
public class SendGridEmailGateway implements EmailGateway {

    private final SendGrid sendGridClient;
    private final NotificationProperties properties;

    public SendGridEmailGateway(SendGrid sendGridClient, NotificationProperties properties) {
        this.sendGridClient = sendGridClient;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        throw new UnsupportedOperationException("Unimplemented method 'send'");
    }

    private Mail buildMail(EmailMessage message) {
        if(!StringUtils.hasText(message.getHtmlBody()) && !StringUtils.hasText(message.getPlainTextBody())) {
            throw new IllegalArgumentException("Both html and plain text body can't be null!");
        }

        Email from = new Email(properties.getFromEmail(), properties.getFromName());
        Email to = new Email(message.getTo());

        boolean hasHtml = StringUtils.hasText(message.getHtmlBody());
        boolean hasPlain = StringUtils.hasText(message.getPlainTextBody());

        Mail mail = new Mail();
        mail.setFrom(from);
        mail.setSubject(message.getSubject());

        Personalization personalization = new Personalization();
        personalization.addTo(to);
        mail.addPersonalization(personalization);

        if(hasPlain) mail.addContent(new Content("text/plain", message.getPlainTextBody()));
        if(hasHtml) mail.addContent(new Content("text/html",  message.getHtmlBody()));

        return mail;
    }
}