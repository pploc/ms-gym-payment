package com.gym.payment.application;

import com.gym.payment.adapter.out.persistence.PaymentIntentEntity;
import com.gym.payment.adapter.out.persistence.PaymentIntentJpaRepository;
import com.gym.payment.adapter.out.persistence.SePayWebhookReceiptEntity;
import com.gym.payment.adapter.out.persistence.SePayWebhookReceiptJpaRepository;
import com.gym.payment.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.events.v1.PaymentCompletedEvent;
import com.gym.proto.payment.v1.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
class SePayWebhookCompletionService {

    private static final String COMPLETED_TOPIC = "payment.completed.v1";
    private static final String PROVIDER = "SEPAY";

    private final SePayWebhookReceiptJpaRepository receiptRepository;
    private final PaymentIntentJpaRepository paymentRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    @Transactional
    public PaymentApplicationService.WebhookOutcome complete(
            PaymentApplicationService.SePayWebhook webhook,
            String payloadSha256,
            boolean eligibleForCompletion) {
        SePayWebhookReceiptEntity existing = receiptRepository.findById(webhook.transactionId()).orElse(null);
        if (existing != null) {
            return samePayload(existing, payloadSha256)
                    ? storedOutcome(existing)
                    : PaymentApplicationService.WebhookOutcome.conflict();
        }

        PaymentApplicationService.WebhookOutcome outcome = eligibleForCompletion
                ? completeNew(webhook)
                : PaymentApplicationService.WebhookOutcome.ignored();
        SePayWebhookReceiptEntity receipt = receipt(webhook, payloadSha256, outcome);
        receiptRepository.save(receipt);
        return outcome;
    }

    private PaymentApplicationService.WebhookOutcome completeNew(PaymentApplicationService.SePayWebhook webhook) {
        if (webhook.code() == null || webhook.code().isBlank()) {
            return PaymentApplicationService.WebhookOutcome.unmatched();
        }
        PaymentIntentEntity intent = paymentRepository.findByPaymentCode(webhook.code()).orElse(null);
        if (intent == null) {
            return PaymentApplicationService.WebhookOutcome.unmatched();
        }
        if (webhook.transferAmountVnd() < intent.getIntentAmountVnd()) {
            // ponytail: one transfer must cover the intent; aggregate receipts only if split payments become a product requirement.
            return PaymentApplicationService.WebhookOutcome.underpaid(intent.getId());
        }
        if (intent.getStatus() == PaymentStatus.PAYMENT_STATUS_COMPLETED) {
            return PaymentApplicationService.WebhookOutcome.extra(intent.getId());
        }

        Instant now = Instant.now(clock);
        intent.setReceivedAmountVnd(webhook.transferAmountVnd());
        intent.setProviderTransactionId(webhook.transactionId());
        intent.setProviderReferenceCode(webhook.referenceCode());
        intent.setCompletedAt(now);
        intent.setUpdatedAt(now);
        intent.setStatus(PaymentStatus.PAYMENT_STATUS_COMPLETED);
        paymentRepository.save(intent);
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setPaymentId(intent.getId())
                .setUserId(intent.getUserId())
                .setType(intent.getPaymentType())
                .setReferenceId(intent.getReferenceId())
                .setAmountVnd(intent.getIntentAmountVnd())
                .setProvider(PROVIDER)
                .setGymId(intent.getGymId())
                .setTimestamp(now.toEpochMilli())
                .build();
        outboxEventWriter.write("payment_intent", intent.getId(), COMPLETED_TOPIC, event,
                "payment.completed.v1:" + intent.getId());
        return PaymentApplicationService.WebhookOutcome.completed(intent.getId());
    }

    private SePayWebhookReceiptEntity receipt(
            PaymentApplicationService.SePayWebhook webhook,
            String payloadSha256,
            PaymentApplicationService.WebhookOutcome outcome) {
        SePayWebhookReceiptEntity receipt = new SePayWebhookReceiptEntity();
        receipt.setProviderTransactionId(webhook.transactionId());
        receipt.setPaymentId(outcome.paymentId());
        receipt.setPaymentCode(webhook.code());
        receipt.setTransferType(webhook.transferType());
        receipt.setTransferAmountVnd(webhook.transferAmountVnd());
        receipt.setReferenceCode(webhook.referenceCode());
        receipt.setPayloadSha256(payloadSha256);
        receipt.setOutcome(outcome.outcome());
        receipt.setReceivedAt(Instant.now(clock));
        return receipt;
    }

    private static boolean samePayload(SePayWebhookReceiptEntity receipt, String payloadSha256) {
        return java.security.MessageDigest.isEqual(
                receipt.getPayloadSha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                payloadSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static PaymentApplicationService.WebhookOutcome storedOutcome(SePayWebhookReceiptEntity receipt) {
        return new PaymentApplicationService.WebhookOutcome(receipt.getPaymentId(), receipt.getOutcome());
    }
}
