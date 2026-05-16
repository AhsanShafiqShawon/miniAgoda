package com.miniagoda.payment.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miniagoda.payment.service.PaymentWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;


@RestController
@RequestMapping("/webhooks")
public class PaymentWebhookController {

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final PaymentWebhookService paymentWebhookService;

    public PaymentWebhookController(PaymentWebhookService paymentWebhookService) {
        this.paymentWebhookService = paymentWebhookService;
    }

    public ResponseEntity<Void> handleStripeWebHook(
        @RequestBody String payload, 
        @RequestHeader("Stripe-Signature") String sigHeader) {
            Event event;

            try {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            } catch (SignatureVerificationException e) {
                return ResponseEntity.badRequest().build();
            }

            paymentWebhookService.handleEvent(event);

            return ResponseEntity.ok().build();
        }
}