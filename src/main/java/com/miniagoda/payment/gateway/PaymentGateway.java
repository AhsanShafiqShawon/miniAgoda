package com.miniagoda.payment.gateway;

import com.miniagoda.payment.dto.PaymentGatewayRequest;
import com.miniagoda.payment.dto.PaymentIntentResponse;
import com.miniagoda.payment.dto.RefundGatewayRequest;
import com.miniagoda.payment.dto.RefundResponse;

public interface PaymentGateway {

    PaymentIntentResponse createPayment(PaymentGatewayRequest request) throws Exception;
    RefundResponse refund(RefundGatewayRequest request) throws Exception;
    PaymentEvent parseWebhook(String payload, String signature) throws Exception;
}