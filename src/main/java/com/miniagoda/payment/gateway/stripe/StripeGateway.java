package com.miniagoda.payment.gateway.stripe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miniagoda.payment.config.StripeConfig;
import com.miniagoda.payment.dto.PaymentGatewayRequest;
import com.miniagoda.payment.dto.PaymentIntentResponse;
import com.miniagoda.payment.dto.RefundGatewayRequest;
import com.miniagoda.payment.dto.RefundResponse;
import com.miniagoda.payment.gateway.PaymentEvent;
import com.miniagoda.payment.gateway.PaymentGateway;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

import java.math.BigDecimal;

import org.springframework.util.StringUtils;

public class StripeGateway implements PaymentGateway {

    private final StripeConfig stripeConfig;

    public StripeGateway(StripeConfig stripeConfig) {
        this.stripeConfig = stripeConfig;
    }

    @Override
    public PaymentIntentResponse createPayment(PaymentGatewayRequest request) throws Exception {
        long amountInSmallestUnit = request.getAmount().movePointRight(2).longValueExact();
        
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
        .setAmount(amountInSmallestUnit)
        .setCurrency(request.getCurrency().toLowerCase())
        .putMetadata("bookingId", request.getBookingId().toString())
        .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
        .setAutomaticPaymentMethods(
            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                .setEnabled(true)
                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                .build()
        )
        .build();

        PaymentIntent intent = PaymentIntent.create(params);

        return new PaymentIntentResponse(
            request.getBookingId(),
            intent.getId(),
            request.getAmount(),
            request.getCurrency()
        );
    }

    @Override
    public RefundResponse refund(RefundGatewayRequest request) throws Exception {
        PaymentIntent intent = PaymentIntent.retrieve(request.getGatewayPaymentId());
        String chargeId = intent.getLatestCharge();

        if(chargeId == null) {
            throw new IllegalStateException("No charge found for GatewayPaymentId: " + request.getGatewayPaymentId());
        }

        long amountInSmallestUnit = request.getAmount().movePointRight(2).longValueExact();

        RefundCreateParams.Builder builder = RefundCreateParams.builder()
        .setCharge(chargeId)
        .setAmount(amountInSmallestUnit);

        if(StringUtils.hasText(request.getReason())) {
            builder.setReason(RefundCreateParams.Reason.valueOf(
                request.getReason().toUpperCase().replace("-", "_")));
        }

        Refund refund = Refund.create(builder.build());

        BigDecimal refundedAmount = new BigDecimal(refund.getAmount()).movePointLeft(2);

        return new RefundResponse(
            request.getPaymentId(),
            refundedAmount,
            refund.getCurrency().toUpperCase()
        );
    }

    @Override
    public PaymentEvent parseWebhook(String payload, String signature) throws Exception {
        Event event;

        try {
            event = Webhook.constructEvent(payload, signature, stripeConfig.getWebhookSecret());
        } catch(SignatureVerificationException e) {
            throw new RuntimeException("Stripe: invalid webhook signature", e);
        }

        return switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                JsonObject obj = JsonParser.parseString(
                    event.getDataObjectDeserializer().getRawJson()).getAsJsonObject();
                yield PaymentEvent.builder()
                    .type(PaymentEvent.Type.PAYMENT_SUCCEEDED)
                    .paymentId(obj.get("id").getAsString())
                    .bookingId(obj.getAsJsonObject("metadata").get("bookingId").getAsString())
                    .amount(obj.get("amount_received").getAsLong())
                    .currency(obj.get("currency").getAsString())
                    .build();
            }
            case "payment_intent.payment_failed" -> {
                JsonObject obj = JsonParser.parseString(
                    event.getDataObjectDeserializer().getRawJson()).getAsJsonObject();
                JsonObject lastError = obj.has("last_payment_error") && !obj.get("last_payment_error").isJsonNull()
                    ? obj.getAsJsonObject("last_payment_error") : null;
                String msg = lastError != null && lastError.has("message")
                    ? lastError.get("message").getAsString() : "Unknown";
                yield PaymentEvent.builder()
                    .type(PaymentEvent.Type.PAYMENT_FAILED)
                    .paymentId(obj.get("id").getAsString())
                    .bookingId(obj.getAsJsonObject("metadata").get("bookingId").getAsString())
                    .failureMessage(msg)
                    .build();
            }
            case "charge.refunded" -> {
                JsonObject obj = JsonParser.parseString(
                    event.getDataObjectDeserializer().getRawJson()).getAsJsonObject();
                yield PaymentEvent.builder()
                    .type(PaymentEvent.Type.REFUND_SUCCEEDED)
                    .paymentId(obj.get("payment_intent").getAsString())
                    .amount(obj.get("amount_refunded").getAsLong())
                    .currency(obj.get("currency").getAsString())
                    .build();
            }
            default -> unknown();
        };
    }

    private PaymentEvent unknown() {
        return PaymentEvent.builder().type(PaymentEvent.Type.UNKNOWN).build();
    }
}