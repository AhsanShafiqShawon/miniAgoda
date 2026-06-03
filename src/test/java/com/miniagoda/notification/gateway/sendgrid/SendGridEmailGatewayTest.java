package com.miniagoda.notification.gateway.sendgrid;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.miniagoda.notification.config.NotificationProperties;
import com.miniagoda.notification.dto.EmailMessage;
import com.miniagoda.notification.exception.NotificationException;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendGridEmailGatewayTest {

    @Mock
    private SendGrid sendGridClient;

    private NotificationProperties properties;
    private SendGridEmailGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setFromEmail("noreply@miniagoda.com");
        properties.setFromName("MiniAgoda");

        gateway = new SendGridEmailGateway(sendGridClient, properties);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Response responseWithStatus(int status) {
        Response r = new Response();
        r.setStatusCode(status);
        r.setBody("");
        return r;
    }

    private EmailMessage htmlOnlyMessage() {
        return new EmailMessage(
                "guest@example.com",
                "Your booking confirmation",
                "<h1>Welcome</h1>",
                null
        );
    }

    private EmailMessage plainOnlyMessage() {
        return new EmailMessage(
                "guest@example.com",
                "Your booking confirmation",
                null,
                "Welcome"
        );
    }

    private EmailMessage fullMessage() {
        return new EmailMessage(
                "guest@example.com",
                "Your booking confirmation",
                "<h1>Welcome</h1>",
                "Welcome"
        );
    }

    // -------------------------------------------------------------------------
    // Happy-path tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("send() – successful delivery")
    class SuccessfulSend {

        @Test
        @DisplayName("returns normally when SendGrid responds with 202")
        void send_accepted202_doesNotThrow() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(202));

            gateway.send(htmlOnlyMessage());

            verify(sendGridClient).api(any(Request.class));
        }

        @Test
        @DisplayName("accepts any 2xx status code (e.g. 200)")
        void send_ok200_doesNotThrow() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(200));

            gateway.send(htmlOnlyMessage());

            verify(sendGridClient).api(any(Request.class));
        }

        @Test
        @DisplayName("sends to the correct SendGrid endpoint via POST")
        void send_usesCorrectEndpointAndMethod() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(202));

            gateway.send(htmlOnlyMessage());

            ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
            verify(sendGridClient).api(captor.capture());

            Request captured = captor.getValue();
            assertThat(captured.getEndpoint()).isEqualTo("mail/send");
            assertThat(captured.getMethod().name()).isEqualTo("POST");
        }

        @Test
        @DisplayName("request body is non-blank")
        void send_requestBodyIsPopulated() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(202));

            gateway.send(fullMessage());

            ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
            verify(sendGridClient).api(captor.capture());
            assertThat(captor.getValue().getBody()).isNotBlank();
        }

        @Test
        @DisplayName("plain-text-only message is accepted")
        void send_plainTextOnly_doesNotThrow() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(202));

            gateway.send(plainOnlyMessage());

            verify(sendGridClient).api(any(Request.class));
        }

        @Test
        @DisplayName("message with both html and plain-text bodies is accepted")
        void send_bothBodies_doesNotThrow() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(202));

            gateway.send(fullMessage());

            verify(sendGridClient).api(any(Request.class));
        }
    }

    // -------------------------------------------------------------------------
    // Error-response tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("send() – SendGrid error responses")
    class ErrorResponses {

        @Test
        @DisplayName("throws NotificationException on 4xx response")
        void send_4xxResponse_throwsNotificationException() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(400));

            assertThatThrownBy(() -> gateway.send(htmlOnlyMessage()))
                    .isInstanceOf(NotificationException.class)
                    .hasMessageContaining("400");
        }

        @Test
        @DisplayName("throws NotificationException on 5xx response")
        void send_5xxResponse_throwsNotificationException() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(500));

            assertThatThrownBy(() -> gateway.send(htmlOnlyMessage()))
                    .isInstanceOf(NotificationException.class)
                    .hasMessageContaining("500");
        }

        @Test
        @DisplayName("throws NotificationException on 199 (below 2xx range)")
        void send_199Response_throwsNotificationException() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(199));

            assertThatThrownBy(() -> gateway.send(htmlOnlyMessage()))
                    .isInstanceOf(NotificationException.class);
        }

        @Test
        @DisplayName("throws NotificationException on 300 (above 2xx range)")
        void send_300Response_throwsNotificationException() throws IOException {
            when(sendGridClient.api(any())).thenReturn(responseWithStatus(300));

            assertThatThrownBy(() -> gateway.send(htmlOnlyMessage()))
                    .isInstanceOf(NotificationException.class);
        }
    }

    // -------------------------------------------------------------------------
    // IOException handling
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("send() – IOException from SendGrid client")
    class IOExceptionHandling {

        @Test
        @DisplayName("wraps IOException in NotificationException")
        void send_ioException_throwsNotificationException() throws IOException {
            when(sendGridClient.api(any())).thenThrow(new IOException("network error"));

            assertThatThrownBy(() -> gateway.send(htmlOnlyMessage()))
                    .isInstanceOf(NotificationException.class)
                    .hasMessageContaining("SendGrid")
                    .hasCauseInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("original IOException is preserved as the cause")
        void send_ioException_originalCauseIsPreserved() throws IOException {
            IOException original = new IOException("timeout");
            when(sendGridClient.api(any())).thenThrow(original);

            assertThatThrownBy(() -> gateway.send(htmlOnlyMessage()))
                    .isInstanceOf(NotificationException.class)
                    .cause()
                    .isSameAs(original);
        }
    }

    // -------------------------------------------------------------------------
    // buildMail() validation tests (exercised via send())
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("buildMail() – input validation")
    class BuildMailValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when both bodies are null")
        void send_bothBodiesNull_throwsIllegalArgumentException() {
            EmailMessage message = new EmailMessage(
                    "guest@example.com", "Subject", null, null);

            assertThatThrownBy(() -> gateway.send(message))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when both bodies are blank")
        void send_bothBodiesBlank_throwsIllegalArgumentException() {
            EmailMessage message = new EmailMessage(
                    "guest@example.com", "Subject", "   ", "   ");

            assertThatThrownBy(() -> gateway.send(message))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when subject is null")
        void send_nullSubject_throwsIllegalArgumentException() {
            EmailMessage message = new EmailMessage(
                    "guest@example.com", null, "<p>Hi</p>", null);

            assertThatThrownBy(() -> gateway.send(message))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("subject");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when subject is blank")
        void send_blankSubject_throwsIllegalArgumentException() {
            EmailMessage message = new EmailMessage(
                    "guest@example.com", "   ", "<p>Hi</p>", null);

            assertThatThrownBy(() -> gateway.send(message))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("subject");
        }
    }
}