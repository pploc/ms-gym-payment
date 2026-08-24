package com.gym.payment.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.payment.application.PaymentApplicationService;
import com.gym.payment.config.PaymentServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SePayWebhookControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    private PaymentApplicationService paymentService;
    private SePayWebhookController controller;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentApplicationService.class);
        PaymentServiceProperties properties = new PaymentServiceProperties(
                new PaymentServiceProperties.OutboxProperties(10, null, null, 3, null),
                new PaymentServiceProperties.SePayProperties("secret", "account", "bank", "001", "VCB"));
        controller = new SePayWebhookController(
                paymentService,
                properties,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void given_signed_inbound_callback_when_receive_then_completes_and_acknowledges() throws Exception {
        byte[] body = body(7, "GYMABC", "in", 500_000L);
        String timestamp = Long.toString(NOW.getEpochSecond());
        when(paymentService.complete(any(), eq(sha256(body)), eq(true)))
                .thenReturn(new PaymentApplicationService.WebhookOutcome("payment-1", "MATCHED"));

        var response = controller.receive(signature(timestamp, body), timestamp, body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("success", true), response.getBody());
        verify(paymentService).complete(new PaymentApplicationService.SePayWebhook(
                7L, "GYMABC", "in", 500_000L, "bank-ref"), sha256(body), true);
    }

    @Test
    void given_stale_signed_callback_when_receive_then_rejects_before_completion() throws Exception {
        byte[] body = body(7, "GYMABC", "in", 500_000L);
        String timestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());

        var response = controller.receive(signature(timestamp, body), timestamp, body);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void given_outbound_callback_when_receive_then_persists_ignored_receipt() throws Exception {
        byte[] body = body(8, "GYMABC", "out", 500_000L);
        String timestamp = Long.toString(NOW.getEpochSecond());
        when(paymentService.complete(any(), eq(sha256(body)), eq(false)))
                .thenReturn(new PaymentApplicationService.WebhookOutcome(null, "IGNORED"));

        var response = controller.receive(signature(timestamp, body), timestamp, body);

        assertEquals(200, response.getStatusCode().value());
        verify(paymentService).complete(new PaymentApplicationService.SePayWebhook(
                8L, "GYMABC", "out", 500_000L, "bank-ref"), sha256(body), false);
    }

    @Test
    void given_oversized_callback_when_receive_then_rejects_before_signature_check() {
        byte[] body = new byte[32 * 1024 + 1];

        var response = controller.receive("invalid", Long.toString(NOW.getEpochSecond()), body);

        assertEquals(413, response.getStatusCode().value());
    }

    private static byte[] body(long id, String code, String transferType, long amount) {
        return ("{\"id\":" + id + ",\"accountNumber\":\"account\",\"code\":\"" + code
                + "\",\"transferType\":\"" + transferType + "\",\"transferAmount\":" + amount
                + ",\"referenceCode\":\"bank-ref\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static String signature(String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static String sha256(byte[] body) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body));
    }
}
