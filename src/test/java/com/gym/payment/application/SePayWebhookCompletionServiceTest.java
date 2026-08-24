package com.gym.payment.application;

import com.gym.payment.adapter.out.persistence.PaymentIntentEntity;
import com.gym.payment.adapter.out.persistence.PaymentIntentJpaRepository;
import com.gym.payment.adapter.out.persistence.SePayWebhookReceiptEntity;
import com.gym.payment.adapter.out.persistence.SePayWebhookReceiptJpaRepository;
import com.gym.payment.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.common.v1.PaymentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SePayWebhookCompletionServiceTest {

    @Mock
    private SePayWebhookReceiptJpaRepository receiptRepository;

    @Mock
    private PaymentIntentJpaRepository paymentRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Test
    void given_new_callback_when_completion_fails_then_receipt_is_not_persisted() {
        PaymentIntentEntity intent = pendingIntent();
        when(receiptRepository.findById(7L)).thenReturn(Optional.empty());
        when(paymentRepository.findByPaymentCode("GYMABC")).thenReturn(Optional.of(intent));
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxEventWriter).write(any(), any(), any(), any(), any());
        SePayWebhookCompletionService service = service();

        assertThrows(IllegalStateException.class, () -> service.complete(webhook(), "hash", true));

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void given_completed_callback_when_completion_replayed_then_reuses_stored_outcome() {
        SePayWebhookReceiptEntity receipt = new SePayWebhookReceiptEntity();
        receipt.setPaymentId("payment-1");
        receipt.setPayloadSha256("hash");
        receipt.setOutcome("MATCHED");
        when(receiptRepository.findById(7L)).thenReturn(Optional.of(receipt));
        SePayWebhookCompletionService service = service();

        PaymentApplicationService.WebhookOutcome outcome = service.complete(webhook(), "hash", true);

        assertEquals(new PaymentApplicationService.WebhookOutcome("payment-1", "MATCHED"), outcome);
        verify(paymentRepository, never()).findByPaymentCode(any());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void given_new_callback_when_completion_succeeds_then_receipt_records_final_outcome() {
        PaymentIntentEntity intent = pendingIntent();
        when(receiptRepository.findById(7L)).thenReturn(Optional.empty());
        when(paymentRepository.findByPaymentCode("GYMABC")).thenReturn(Optional.of(intent));
        SePayWebhookCompletionService service = service();

        PaymentApplicationService.WebhookOutcome outcome = service.complete(webhook(), "hash", true);

        ArgumentCaptor<SePayWebhookReceiptEntity> receipt = ArgumentCaptor.forClass(SePayWebhookReceiptEntity.class);
        verify(receiptRepository).save(receipt.capture());
        assertEquals("MATCHED", outcome.outcome());
        assertEquals("MATCHED", receipt.getValue().getOutcome());
        assertEquals("payment-1", receipt.getValue().getPaymentId());
    }

    private SePayWebhookCompletionService service() {
        return new SePayWebhookCompletionService(
                receiptRepository,
                paymentRepository,
                outboxEventWriter,
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC));
    }

    private static PaymentApplicationService.SePayWebhook webhook() {
        return new PaymentApplicationService.SePayWebhook(7L, "GYMABC", "in", 500_000L, "bank-ref");
    }

    private static PaymentIntentEntity pendingIntent() {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setId("payment-1");
        intent.setUserId("user-1");
        intent.setGymId("gym-1");
        intent.setReferenceId("purchase-1");
        intent.setPaymentType(PaymentType.PAYMENT_TYPE_MEMBERSHIP);
        intent.setIntentAmountVnd(500_000L);
        intent.setPaymentCode("GYMABC");
        return intent;
    }
}
