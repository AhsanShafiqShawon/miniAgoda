package com.miniagoda.notification.gateway.sendgrid;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.miniagoda.notification.config.NotificationProperties;
import com.miniagoda.notification.dto.EmailMessage;
import com.miniagoda.notification.exception.NotificationException;
import com.miniagoda.notification.gateway.EmailGateway;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;

@Component
public class SendGridEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailGateway.class);
    private final SendGrid sendGridClient;
    private final NotificationProperties properties;

    public SendGridEmailGateway(SendGrid sendGridClient, NotificationProperties properties) {
        this.sendGridClient = sendGridClient;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        
        Mail mail = buildMail(message);

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");

        try {
            request.setBody(mail.build());
            
            Response response = sendGridClient.api(request);
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                log.error("SendGrid rejected email to={} subject={} status={} body={}",
                message.getTo(), message.getSubject(),
                response.getStatusCode(), response.getBody());
                
                throw new NotificationException("SendGrid API call failed with status: " + response.getStatusCode());
            }
            
            log.info("Email sent successfully to={} subject={} status={}",
            message.getTo(), message.getSubject(),
            response.getStatusCode());
        }
        catch(IOException e) {
            log.error("Failed to send email via SendGrid to={} subject={}",
            message.getTo(),
            message.getSubject(), e);
            
            throw new NotificationException("Failed to send email via SendGrid", e);
        }
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
 
        if (!StringUtils.hasText(message.getSubject())) {
            throw new IllegalArgumentException("Email subject cannot be blank");
        }
        mail.setSubject(message.getSubject());

        Personalization personalization = new Personalization();
        personalization.addTo(to);
        mail.addPersonalization(personalization);

        if(hasPlain) {
            mail.addContent(new Content("text/plain", message.getPlainTextBody()));
        }

        if(hasHtml) {
            mail.addContent(new Content("text/html",  message.getHtmlBody()));
        }

        return mail;
    }
}