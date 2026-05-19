package com.miniagoda.payment.config;

import com.miniagoda.payment.gateway.PaymentGateway;
import com.miniagoda.payment.gateway.stripe.StripeGateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(StripeConfig stripeConfig) {
        return new StripeGateway(stripeConfig);
    }
}