package com.gym.payment.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.payment.application.PaymentApplicationService;
import com.gym.payment.config.PaymentServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class SePayWebhookController {

    private static final long MAX_CLOCK_SKEW_SECONDS = 300;
    private static final int MAX_BODY_BYTES = 32 * 1024;

    private final PaymentApplicationService paymentService;
    private final PaymentServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @PostMapping(value = "/sepay", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Boolean>> receive(
            @RequestHeader(name = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(name = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestBody byte[] rawBody) {
        if (rawBody.length == 0 || rawBody.length > MAX_BODY_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("success", false));
        }
        if (!validSignature(signature, timestamp, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }
        SePayPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, SePayPayload.class);
        } catch (Exception invalidJson) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        if (!isValid(payload)) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }

        PaymentApplicationService.SePayWebhook webhook = new PaymentApplicationService.SePayWebhook(
                payload.id(), payload.code(), payload.transferType(), payload.transferAmount(), payload.referenceCode());
        String hash = sha256(rawBody);
        try {
            return response(complete(webhook, hash, payload));
        } catch (DataIntegrityViolationException duplicateRace) {
            return response(complete(webhook, hash, payload));
        }
    }

    private PaymentApplicationService.WebhookOutcome complete(
            PaymentApplicationService.SePayWebhook webhook, String hash, SePayPayload payload) {
        return paymentService.complete(webhook, hash,
                "in".equals(payload.transferType()) && properties.sepay().accountNumber().equals(payload.accountNumber()));
    }

    private static ResponseEntity<Map<String, Boolean>> response(PaymentApplicationService.WebhookOutcome outcome) {
        return "CONFLICT".equals(outcome.outcome())
                ? ResponseEntity.badRequest().body(Map.of("success", false))
                : ResponseEntity.ok(Map.of("success", true));
    }

    private boolean validSignature(String signature, String timestamp, byte[] rawBody) {
        if (signature == null || timestamp == null || properties.sepay().webhookSecret().isBlank()) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now(clock).getEpochSecond() - seconds) > MAX_CLOCK_SKEW_SECONDS) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.sepay().webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception invalidHeader) {
            return false;
        }
    }

    private static boolean isValid(SePayPayload payload) {
        return payload != null && payload.id() > 0 && payload.accountNumber() != null && !payload.accountNumber().isBlank()
                && payload.transferType() != null && !payload.transferType().isBlank() && payload.transferAmount() > 0;
    }

    private static String sha256(byte[] rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawBody));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SePayPayload(
            long id,
            String gateway,
            String transactionDate,
            String accountNumber,
            String subAccount,
            String code,
            String content,
            String transferType,
            String description,
            long transferAmount,
            long accumulated,
            String referenceCode) {}
}
