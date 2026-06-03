package com.miniagoda.payment.gateway.stripe;

import com.miniagoda.payment.config.StripeConfig;
import com.miniagoda.payment.dto.PaymentGatewayRequest;
import com.miniagoda.payment.dto.PaymentIntentResponse;
import com.miniagoda.payment.dto.RefundGatewayRequest;
import com.miniagoda.payment.dto.RefundResponse;
import com.miniagoda.payment.gateway.PaymentEvent;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeGatewayTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String PAYMENT_INTENT_ID = "pi_test_123";
    private static final String CHARGE_ID = "ch_test_456";

    @Mock
    private StripeConfig stripeConfig;

    private StripeGateway stripeGateway;

    @BeforeEach
    void setUp() {
        // NOTE: getWebhookSecret() is only called during parseWebhook; stubbing it here
        // would trigger UnnecessaryStubbingException for CreatePayment / RefundTests nested
        // classes. It is instead stubbed inside each ParseWebhook test via buildMockGateway().
        stripeGateway = new StripeGateway(stripeConfig);
    }

    // -------------------------------------------------------------------------
    // createPayment
    // -------------------------------------------------------------------------

    @Nested
    class CreatePayment {

        @Test
        void shouldReturnPaymentIntentResponse_whenStripeSucceeds() throws Exception {
            UUID bookingId = UUID.randomUUID();
            PaymentGatewayRequest request = new PaymentGatewayRequest(
                bookingId,
                new BigDecimal("150.00"),
                "THB"
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getId()).thenReturn(PAYMENT_INTENT_ID);

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockIntent);

                PaymentIntentResponse response = stripeGateway.createPayment(request);

                assertThat(response.getBookingId()).isEqualTo(bookingId);
                assertThat(response.getPaymentToken()).isEqualTo(PAYMENT_INTENT_ID);
                assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
                assertThat(response.getCurrency()).isEqualTo("THB");
            }
        }

        @Test
        void shouldConvertAmountToSmallestUnit() throws Exception {
            // 99.99 USD → 9999 cents passed to Stripe
            UUID bookingId = UUID.randomUUID();
            PaymentGatewayRequest request = new PaymentGatewayRequest(
                bookingId,
                new BigDecimal("99.99"),
                "USD"
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getId()).thenReturn(PAYMENT_INTENT_ID);

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                // Capture the params to assert the converted amount
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenAnswer(invocation -> {
                        PaymentIntentCreateParams params = invocation.getArgument(0);
                        assertThat(params.getAmount()).isEqualTo(9999L);
                        return mockIntent;
                    });

                stripeGateway.createPayment(request);
            }
        }

        @Test
        void shouldPassCurrencyAsLowerCase() throws Exception {
            PaymentGatewayRequest request = new PaymentGatewayRequest(
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "USD" // uppercase — should be lowercased before passing to Stripe
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getId()).thenReturn(PAYMENT_INTENT_ID);

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenAnswer(invocation -> {
                        PaymentIntentCreateParams params = invocation.getArgument(0);
                        assertThat(params.getCurrency()).isEqualTo("usd");
                        return mockIntent;
                    });

                stripeGateway.createPayment(request);
            }
        }

        @Test
        void shouldPropagateException_whenStripeThrows() {
            PaymentGatewayRequest request = new PaymentGatewayRequest(
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "USD"
            );

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenThrow(new RuntimeException("Stripe API error"));

                assertThatThrownBy(() -> stripeGateway.createPayment(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Stripe API error");
            }
        }
    }

    // -------------------------------------------------------------------------
    // refund
    // -------------------------------------------------------------------------

    @Nested
    class RefundTests {

        @Test
        void shouldReturnRefundResponse_whenRefundSucceeds() throws Exception {
            UUID paymentId = UUID.randomUUID();
            RefundGatewayRequest request = new RefundGatewayRequest(
                paymentId,
                PAYMENT_INTENT_ID,
                new BigDecimal("50.00"),
                "THB",
                null
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getLatestCharge()).thenReturn(CHARGE_ID);

            Refund mockRefund = mock(Refund.class);
            when(mockRefund.getAmount()).thenReturn(5000L);
            when(mockRefund.getCurrency()).thenReturn("thb");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
                 MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {

                piStatic.when(() -> PaymentIntent.retrieve(PAYMENT_INTENT_ID))
                    .thenReturn(mockIntent);
                refundStatic.when(() -> Refund.create(any(RefundCreateParams.class)))
                    .thenReturn(mockRefund);

                RefundResponse response = stripeGateway.refund(request);

                assertThat(response.getPaymentId()).isEqualTo(paymentId);
                assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
                assertThat(response.getCurrency()).isEqualTo("THB"); // uppercased in response
            }
        }

        @Test
        void shouldThrowIllegalStateException_whenNoChargeFound() {
            RefundGatewayRequest request = new RefundGatewayRequest(
                UUID.randomUUID(),
                PAYMENT_INTENT_ID,
                new BigDecimal("50.00"),
                "THB",
                null
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getLatestCharge()).thenReturn(null);

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
                piStatic.when(() -> PaymentIntent.retrieve(PAYMENT_INTENT_ID))
                    .thenReturn(mockIntent);

                assertThatThrownBy(() -> stripeGateway.refund(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No charge found for GatewayPaymentId")
                    .hasMessageContaining(PAYMENT_INTENT_ID);
            }
        }

        @Test
        void shouldConvertRefundAmountFromSmallestUnit() throws Exception {
            // Stripe returns amount in cents: 7550 → BigDecimal("75.50")
            RefundGatewayRequest request = new RefundGatewayRequest(
                UUID.randomUUID(),
                PAYMENT_INTENT_ID,
                new BigDecimal("75.50"),
                "USD",
                null
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getLatestCharge()).thenReturn(CHARGE_ID);

            Refund mockRefund = mock(Refund.class);
            when(mockRefund.getAmount()).thenReturn(7550L);
            when(mockRefund.getCurrency()).thenReturn("usd");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
                 MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {

                piStatic.when(() -> PaymentIntent.retrieve(PAYMENT_INTENT_ID))
                    .thenReturn(mockIntent);
                refundStatic.when(() -> Refund.create(any(RefundCreateParams.class)))
                    .thenReturn(mockRefund);

                RefundResponse response = stripeGateway.refund(request);

                assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("75.50"));
            }
        }

        @Test
        void shouldSetRefundReason_whenReasonIsProvided() throws Exception {
            RefundGatewayRequest request = new RefundGatewayRequest(
                UUID.randomUUID(),
                PAYMENT_INTENT_ID,
                new BigDecimal("30.00"),
                "USD",
                "fraudulent" // maps to RefundCreateParams.Reason.FRAUDULENT
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getLatestCharge()).thenReturn(CHARGE_ID);

            Refund mockRefund = mock(Refund.class);
            when(mockRefund.getAmount()).thenReturn(3000L);
            when(mockRefund.getCurrency()).thenReturn("usd");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
                 MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {

                piStatic.when(() -> PaymentIntent.retrieve(PAYMENT_INTENT_ID))
                    .thenReturn(mockIntent);
                refundStatic.when(() -> Refund.create(any(RefundCreateParams.class)))
                    .thenAnswer(invocation -> {
                        RefundCreateParams params = invocation.getArgument(0);
                        assertThat(params.getReason())
                            .isEqualTo(RefundCreateParams.Reason.FRAUDULENT);
                        return mockRefund;
                    });

                stripeGateway.refund(request);
            }
        }

        @Test
        void shouldNotSetRefundReason_whenReasonIsBlank() throws Exception {
            RefundGatewayRequest request = new RefundGatewayRequest(
                UUID.randomUUID(),
                PAYMENT_INTENT_ID,
                new BigDecimal("30.00"),
                "USD",
                "  " // blank — should not set reason
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getLatestCharge()).thenReturn(CHARGE_ID);

            Refund mockRefund = mock(Refund.class);
            when(mockRefund.getAmount()).thenReturn(3000L);
            when(mockRefund.getCurrency()).thenReturn("usd");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
                 MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {

                piStatic.when(() -> PaymentIntent.retrieve(PAYMENT_INTENT_ID))
                    .thenReturn(mockIntent);
                refundStatic.when(() -> Refund.create(any(RefundCreateParams.class)))
                    .thenAnswer(invocation -> {
                        RefundCreateParams params = invocation.getArgument(0);
                        assertThat(params.getReason()).isNull();
                        return mockRefund;
                    });

                stripeGateway.refund(request);
            }
        }

        @Test
        void shouldMapDashedReason_toCamelCase() throws Exception {
            // "duplicate-charge" → DUPLICATE_CHARGE (dashes replaced with underscores)
            RefundGatewayRequest request = new RefundGatewayRequest(
                UUID.randomUUID(),
                PAYMENT_INTENT_ID,
                new BigDecimal("20.00"),
                "USD",
                "duplicate"
            );

            PaymentIntent mockIntent = mock(PaymentIntent.class);
            when(mockIntent.getLatestCharge()).thenReturn(CHARGE_ID);

            Refund mockRefund = mock(Refund.class);
            when(mockRefund.getAmount()).thenReturn(2000L);
            when(mockRefund.getCurrency()).thenReturn("usd");

            try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class);
                 MockedStatic<Refund> refundStatic = mockStatic(Refund.class)) {

                piStatic.when(() -> PaymentIntent.retrieve(PAYMENT_INTENT_ID))
                    .thenReturn(mockIntent);
                refundStatic.when(() -> Refund.create(any(RefundCreateParams.class)))
                    .thenReturn(mockRefund);

                // Should not throw — verifies the reason mapping doesn't explode
                assertThat(stripeGateway.refund(request)).isNotNull();
            }
        }
    }

    // -------------------------------------------------------------------------
    // parseWebhook
    // -------------------------------------------------------------------------

    @Nested
    class ParseWebhook {

        private static final String DUMMY_SIGNATURE = "t=1,v1=abc";

        // Stubs getWebhookSecret() here rather than in the outer setUp(), because
        // CreatePayment and RefundTests don't call parseWebhook — leaving the stub
        // unused there would trigger UnnecessaryStubbingException.
        private StripeGateway gatewayWithSecret() {
            when(stripeConfig.getWebhookSecret()).thenReturn(WEBHOOK_SECRET);
            return new StripeGateway(stripeConfig);
        }

        @Test
        void shouldReturnPaymentSucceeded_whenEventTypeMatches() throws Exception {
            String bookingId = UUID.randomUUID().toString();
            String payload = buildPaymentIntentPayload(
                PAYMENT_INTENT_ID, bookingId, 15000L, "thb"
            );

            Event mockEvent = buildMockEvent("payment_intent.succeeded", payload);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(payload, DUMMY_SIGNATURE, WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

                PaymentEvent event = gatewayWithSecret().parseWebhook(payload, DUMMY_SIGNATURE);

                assertThat(event.getType()).isEqualTo(PaymentEvent.Type.PAYMENT_SUCCEEDED);
                assertThat(event.getPaymentId()).isEqualTo(PAYMENT_INTENT_ID);
                assertThat(event.getBookingId()).isEqualTo(bookingId);
                assertThat(event.getAmount()).isEqualTo(15000L);
                assertThat(event.getCurrency()).isEqualTo("thb");
            }
        }

        @Test
        void shouldReturnPaymentFailed_withErrorMessage() throws Exception {
            String bookingId = UUID.randomUUID().toString();
            String payload = buildPaymentFailedPayload(
                PAYMENT_INTENT_ID, bookingId, "Your card has insufficient funds."
            );

            Event mockEvent = buildMockEvent("payment_intent.payment_failed", payload);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(payload, DUMMY_SIGNATURE, WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

                PaymentEvent event = gatewayWithSecret().parseWebhook(payload, DUMMY_SIGNATURE);

                assertThat(event.getType()).isEqualTo(PaymentEvent.Type.PAYMENT_FAILED);
                assertThat(event.getPaymentId()).isEqualTo(PAYMENT_INTENT_ID);
                assertThat(event.getBookingId()).isEqualTo(bookingId);
                assertThat(event.getFailureMessage()).isEqualTo("Your card has insufficient funds.");
            }
        }

        @Test
        void shouldReturnPaymentFailed_withUnknownMessage_whenLastPaymentErrorIsNull() throws Exception {
            String bookingId = UUID.randomUUID().toString();
            // last_payment_error is null in the payload
            String payload = buildPaymentFailedPayloadNoError(PAYMENT_INTENT_ID, bookingId);

            Event mockEvent = buildMockEvent("payment_intent.payment_failed", payload);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(payload, DUMMY_SIGNATURE, WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

                PaymentEvent event = gatewayWithSecret().parseWebhook(payload, DUMMY_SIGNATURE);

                assertThat(event.getType()).isEqualTo(PaymentEvent.Type.PAYMENT_FAILED);
                assertThat(event.getFailureMessage()).isEqualTo("Unknown");
            }
        }

        @Test
        void shouldReturnRefundSucceeded_whenChargeRefundedEvent() throws Exception {
            String payload = buildChargeRefundedPayload(PAYMENT_INTENT_ID, 5000L, "usd");

            Event mockEvent = buildMockEvent("charge.refunded", payload);

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(payload, DUMMY_SIGNATURE, WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

                PaymentEvent event = gatewayWithSecret().parseWebhook(payload, DUMMY_SIGNATURE);

                assertThat(event.getType()).isEqualTo(PaymentEvent.Type.REFUND_SUCCEEDED);
                assertThat(event.getPaymentId()).isEqualTo(PAYMENT_INTENT_ID);
                assertThat(event.getAmount()).isEqualTo(5000L);
                assertThat(event.getCurrency()).isEqualTo("usd");
            }
        }

        @Test
        void shouldReturnUnknown_whenEventTypeIsUnrecognised() throws Exception {
            String payload = "{}";
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("some.unhandled.event");

            // For unrecognised events, getRawJson is never called, so no need to stub deserializer

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(payload, DUMMY_SIGNATURE, WEBHOOK_SECRET))
                    .thenReturn(mockEvent);

                PaymentEvent event = gatewayWithSecret().parseWebhook(payload, DUMMY_SIGNATURE);

                assertThat(event.getType()).isEqualTo(PaymentEvent.Type.UNKNOWN);
            }
        }

        @Test
        void shouldThrowRuntimeException_whenSignatureIsInvalid() throws Exception {
            String payload = "{}";
            String badSignature = "bad_sig";

            try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
                webhookStatic.when(() -> Webhook.constructEvent(payload, badSignature, WEBHOOK_SECRET))
                    .thenThrow(new SignatureVerificationException("Invalid signature", badSignature));

                assertThatThrownBy(() -> gatewayWithSecret().parseWebhook(payload, badSignature))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Stripe: invalid webhook signature")
                    .hasCauseInstanceOf(SignatureVerificationException.class);
            }
        }

        // ------------------------------------------------------------------
        // Payload builders — minimal JSON that mirrors what Stripe sends
        // ------------------------------------------------------------------

        private String buildPaymentIntentPayload(String id, String bookingId, long amountReceived, String currency) {
            return """
                {
                  "id": "%s",
                  "amount_received": %d,
                  "currency": "%s",
                  "metadata": { "bookingId": "%s" }
                }
                """.formatted(id, amountReceived, currency, bookingId);
        }

        private String buildPaymentFailedPayload(String id, String bookingId, String errorMessage) {
            return """
                {
                  "id": "%s",
                  "metadata": { "bookingId": "%s" },
                  "last_payment_error": { "message": "%s" }
                }
                """.formatted(id, bookingId, errorMessage);
        }

        private String buildPaymentFailedPayloadNoError(String id, String bookingId) {
            return """
                {
                  "id": "%s",
                  "metadata": { "bookingId": "%s" },
                  "last_payment_error": null
                }
                """.formatted(id, bookingId);
        }

        private String buildChargeRefundedPayload(String paymentIntentId, long amountRefunded, String currency) {
            return """
                {
                  "payment_intent": "%s",
                  "amount_refunded": %d,
                  "currency": "%s"
                }
                """.formatted(paymentIntentId, amountRefunded, currency);
        }

        /**
         * Creates a mock {@link Event} whose {@link EventDataObjectDeserializer#getRawJson()}
         * returns the given payload JSON — matching how the gateway accesses event data.
         */
        private Event buildMockEvent(String type, String rawJson) {
            Event event = mock(Event.class);
            when(event.getType()).thenReturn(type);

            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(deserializer.getRawJson()).thenReturn(rawJson);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);

            return event;
        }
    }
}