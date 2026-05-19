package com.miniagoda.payment.gateway.stripe;

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
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

import java.math.BigDecimal;
import java.util.Optional;

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
        .build();

        PaymentIntent intent = PaymentIntent.create(params);

        return new PaymentIntentResponse(
            request.getBookingId(),
            intent.getClientSecret(),
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

        Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();


        return switch (event.getType()) {
        case "payment_intent.succeeded" -> stripeObject
                .map(obj -> {
                    PaymentIntent intent = (PaymentIntent) obj;
                    return PaymentEvent.builder()
                            .type(PaymentEvent.Type.PAYMENT_SUCCEEDED)
                            .paymentId(intent.getId())
                            .bookingId(intent.getMetadata().get("bookingId"))
                            .amount(intent.getAmountReceived())
                            .currency(intent.getCurrency())
                            .build();
                }).orElse(unknown());
 
        case "payment_intent.payment_failed" -> stripeObject
                .map(obj -> {
                    PaymentIntent intent = (PaymentIntent) obj;
                    String msg = intent.getLastPaymentError() != null
                            ? intent.getLastPaymentError().getMessage() : "Unknown";
                    return PaymentEvent.builder()
                            .type(PaymentEvent.Type.PAYMENT_FAILED)
                            .paymentId(intent.getId())
                            .bookingId(intent.getMetadata().get("bookingId"))
                            .failureMessage(msg)
                            .build();
                }).orElse(unknown());

        case "charge.refunded" -> stripeObject
                .map(obj -> {
                    com.stripe.model.Charge charge = (com.stripe.model.Charge) obj;
                    return PaymentEvent.builder()
                            .type(PaymentEvent.Type.REFUND_SUCCEEDED)
                            .paymentId(charge.getPaymentIntent())
                            .amount(charge.getAmountRefunded())
                            .currency(charge.getCurrency())
                            .build();
                }).orElse(unknown());

        default -> {
            yield unknown();
            }
        };
    }

    private PaymentEvent unknown() {
        return PaymentEvent.builder().type(PaymentEvent.Type.UNKNOWN).build();
    }

}