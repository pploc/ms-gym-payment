package com.gym.payment.application;

import com.gym.payment.adapter.out.persistence.PaymentIntentEntity;
import com.gym.payment.adapter.out.persistence.PaymentIntentJpaRepository;
import com.gym.payment.config.PaymentServiceProperties;
import com.gym.proto.common.v1.PaymentType;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import com.gym.proto.payment.v1.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private static final String PROVIDER = "SEPAY";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentIntentJpaRepository paymentRepository;
    private final PaymentServiceProperties properties;
    private final Clock clock;
    private final SePayWebhookCompletionService webhookCompletionService;

    @Transactional
    public InitiatePaymentResponse initiate(InitiatePaymentRequest request) {
        validateInitiateRequest(request);
        return paymentRepository.findByPaymentTypeAndReferenceId(request.getPaymentType(), request.getReferenceId())
                .map(existing -> reuseOrReject(existing, request))
                .orElseGet(() -> createOrResolveRace(request));
    }

    public PaymentStatusView getStatus(String paymentId) {
        return paymentRepository.findById(paymentId)
                .map(intent -> new PaymentStatusView(intent.getStatus(), intent.getIntentAmountVnd()))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    public WebhookOutcome complete(SePayWebhook webhook, String payloadSha256, boolean eligibleForCompletion) {
        return webhookCompletionService.complete(webhook, payloadSha256, eligibleForCompletion);
    }

    private InitiatePaymentResponse createOrResolveRace(InitiatePaymentRequest request) {
        try {
            return create(request);
        } catch (DataIntegrityViolationException race) {
            PaymentIntentEntity existing = paymentRepository
                    .findByPaymentTypeAndReferenceId(request.getPaymentType(), request.getReferenceId())
                    .orElseThrow(() -> race);
            return reuseOrReject(existing, request);
        }
    }

    private InitiatePaymentResponse create(InitiatePaymentRequest request) {
        Instant now = Instant.now(clock);
        String code = paymentCode();
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setUserId(request.getUserId());
        intent.setGymId(request.getGymId());
        intent.setReferenceId(request.getReferenceId());
        intent.setPaymentType(request.getPaymentType());
        intent.setProvider(PROVIDER);
        intent.setIntentAmountVnd(request.getAmountVnd());
        intent.setPaymentCode(code);
        intent.setPaymentUrl(qrUrl(code, request.getAmountVnd()));
        intent.setCreatedAt(now);
        intent.setUpdatedAt(now);
        paymentRepository.save(intent);
        return response(intent);
    }

    private static void validateInitiateRequest(InitiatePaymentRequest request) {
        requireNonBlank(request.getGymId(), "gym_id is required");
        requireNonBlank(request.getReferenceId(), "reference_id is required");
        requireNonBlank(request.getUserId(), "user_id is required");
        if (request.getPaymentType() != PaymentType.PAYMENT_TYPE_MEMBERSHIP) {
            throw new IllegalArgumentException("payment_type must be MEMBERSHIP");
        }
        if (!PROVIDER.equals(request.getProvider())) {
            throw new IllegalArgumentException("provider must be SEPAY");
        }
        if (!request.getDiscountCode().isBlank()) {
            throw new IllegalArgumentException("discount_code is not supported");
        }
    }

    private InitiatePaymentResponse reuseOrReject(PaymentIntentEntity intent, InitiatePaymentRequest request) {
        if (!intent.getUserId().equals(request.getUserId())
                || !intent.getGymId().equals(request.getGymId())
                || !intent.getProvider().equals(request.getProvider())
                || intent.getIntentAmountVnd() != request.getAmountVnd()) {
            throw new IllegalStateException("Payment intent immutable fields mismatch");
        }
        return response(intent);
    }

    private String qrUrl(String code, long amountVnd) {
        requireNonBlank(properties.sepay().qrAccount(), "SEPAY_QR_ACCOUNT is required");
        requireNonBlank(properties.sepay().qrBank(), "SEPAY_QR_BANK is required");
        return UriComponentsBuilder.fromUriString("https://vietqr.app/img")
                .queryParam("acc", properties.sepay().qrAccount())
                .queryParam("bank", properties.sepay().qrBank())
                .queryParam("amount", amountVnd)
                .queryParam("des", code)
                .build()
                .encode()
                .toUriString();
    }

    private static String paymentCode() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return "GYM" + HexFormat.of().formatHex(bytes).toUpperCase();
    }

    private static InitiatePaymentResponse response(PaymentIntentEntity intent) {
        return InitiatePaymentResponse.newBuilder()
                .setPaymentId(intent.getId())
                .setPaymentUrl(intent.getPaymentUrl())
                .build();
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record PaymentStatusView(PaymentStatus status, long intentAmountVnd) {}

    public record SePayWebhook(
            long transactionId,
            String code,
            String transferType,
            long transferAmountVnd,
            String referenceCode) {}

    public record WebhookOutcome(String paymentId, String outcome) {
        static WebhookOutcome completed(String paymentId) { return new WebhookOutcome(paymentId, "MATCHED"); }
        static WebhookOutcome underpaid(String paymentId) { return new WebhookOutcome(paymentId, "UNDERPAID"); }
        static WebhookOutcome extra(String paymentId) { return new WebhookOutcome(paymentId, "EXTRA_TRANSFER"); }
        static WebhookOutcome unmatched() { return new WebhookOutcome(null, "UNMATCHED"); }
        static WebhookOutcome ignored() { return new WebhookOutcome(null, "IGNORED"); }
        static WebhookOutcome conflict() { return new WebhookOutcome(null, "CONFLICT"); }
    }
}
