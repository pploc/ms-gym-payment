package com.gym.payment.application;

import com.gym.payment.adapter.out.persistence.PaymentIntentEntity;
import com.gym.payment.adapter.out.persistence.PaymentIntentJpaRepository;
import com.gym.payment.config.PaymentServiceProperties;
import com.gym.payment.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.common.v1.PaymentType;
import com.gym.proto.payment.v1.InitiatePaymentRequest;
import com.gym.proto.payment.v1.InitiatePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    @Mock
    private PaymentIntentJpaRepository paymentRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private com.gym.payment.adapter.out.persistence.SePayWebhookReceiptJpaRepository receiptRepository;

    @InjectMocks
    private PaymentApplicationService service;

    private InitiatePaymentRequest request;

    @BeforeEach
    void setUp() {
        PaymentServiceProperties properties = new PaymentServiceProperties(
                new PaymentServiceProperties.OutboxProperties(10, null, null, 3, null),
                new PaymentServiceProperties.SePayProperties("secret", "account", "bank", "001", "VCB"));
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
        service = new PaymentApplicationService(
                paymentRepository,
                properties,
                clock,
                new SePayWebhookCompletionService(receiptRepository, paymentRepository, outboxEventWriter, clock));
        request = InitiatePaymentRequest.newBuilder()
                .setGymId("gym-1")
                .setPaymentType(PaymentType.PAYMENT_TYPE_MEMBERSHIP)
                .setReferenceId("purchase-1")
                .setProvider("SEPAY")
                .setUserId("user-1")
                .setAmountVnd(500_000L)
                .build();
    }

    @Test
    void given_new_membership_intent_when_initiate_then_persists_vietqr_payment() {
        when(paymentRepository.findByPaymentTypeAndReferenceId(PaymentType.PAYMENT_TYPE_MEMBERSHIP, "purchase-1"))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InitiatePaymentResponse response = service.initiate(request);

        ArgumentCaptor<PaymentIntentEntity> payment = ArgumentCaptor.forClass(PaymentIntentEntity.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals(response.getPaymentId(), payment.getValue().getId());
        assertEquals("SEPAY", payment.getValue().getProvider());
        assertEquals(500_000L, payment.getValue().getIntentAmountVnd());
        org.junit.jupiter.api.Assertions.assertTrue(response.getPaymentUrl().contains("amount=500000"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getPaymentUrl().contains("des=GYM"));
    }

    @Test
    void given_existing_matching_intent_when_initiate_then_returns_existing_response() {
        PaymentIntentEntity existing = existingPayment();
        when(paymentRepository.findByPaymentTypeAndReferenceId(PaymentType.PAYMENT_TYPE_MEMBERSHIP, "purchase-1"))
                .thenReturn(Optional.of(existing));

        InitiatePaymentResponse response = service.initiate(request);

        assertEquals("payment-1", response.getPaymentId());
        assertEquals("https://payment.example", response.getPaymentUrl());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void given_non_sepay_provider_when_initiate_then_rejects_without_persistence() {
        InitiatePaymentRequest invalid = request.toBuilder().setProvider("MOMO").build();

        assertThrows(IllegalArgumentException.class, () -> service.initiate(invalid));

        verify(paymentRepository, never()).findByPaymentTypeAndReferenceId(any(), any());
    }

    @Test
    void given_overpayment_when_complete_then_persists_actual_and_emits_frozen_amount() {
        PaymentIntentEntity intent = existingPayment();
        when(receiptRepository.findById(7L)).thenReturn(Optional.empty());
        when(paymentRepository.findByPaymentCode("GYMABC")).thenReturn(Optional.of(intent));

        service.complete(new PaymentApplicationService.SePayWebhook(7L, "GYMABC", "in", 600_000L, "bank-ref"), "hash", true);

        assertEquals(600_000L, intent.getReceivedAmountVnd());
        verify(outboxEventWriter).write(eq("payment_intent"), eq("payment-1"), eq("payment.completed.v1"),
                org.mockito.ArgumentMatchers.argThat(event -> ((com.gym.proto.events.v1.PaymentCompletedEvent) event).getAmountVnd() == 500_000L),
                eq("payment.completed.v1:payment-1"));
    }

    @Test
    void given_duplicate_callback_with_same_hash_when_complete_then_reuses_stored_outcome() {
        com.gym.payment.adapter.out.persistence.SePayWebhookReceiptEntity receipt =
                new com.gym.payment.adapter.out.persistence.SePayWebhookReceiptEntity();
        receipt.setPaymentId("payment-1");
        receipt.setPayloadSha256("hash");
        receipt.setOutcome("MATCHED");
        when(receiptRepository.findById(7L)).thenReturn(Optional.of(receipt));

        PaymentApplicationService.WebhookOutcome outcome = service.complete(
                new PaymentApplicationService.SePayWebhook(7L, "GYMABC", "in", 500_000L, "bank-ref"), "hash", true);

        assertEquals(new PaymentApplicationService.WebhookOutcome("payment-1", "MATCHED"), outcome);
        verify(paymentRepository, never()).findByPaymentCode(any());
        verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void given_duplicate_callback_with_different_hash_when_complete_then_rejects_conflict() {
        com.gym.payment.adapter.out.persistence.SePayWebhookReceiptEntity receipt =
                new com.gym.payment.adapter.out.persistence.SePayWebhookReceiptEntity();
        receipt.setPayloadSha256("original");
        when(receiptRepository.findById(7L)).thenReturn(Optional.of(receipt));

        PaymentApplicationService.WebhookOutcome outcome = service.complete(
                new PaymentApplicationService.SePayWebhook(7L, "GYMABC", "in", 500_000L, "bank-ref"), "changed", true);

        assertEquals("CONFLICT", outcome.outcome());
        verify(paymentRepository, never()).findByPaymentCode(any());
    }

    @Test
    void given_short_payment_when_complete_then_keeps_intent_pending_without_event() {
        PaymentIntentEntity intent = existingPayment();
        when(receiptRepository.findById(7L)).thenReturn(Optional.empty());
        when(paymentRepository.findByPaymentCode("GYMABC")).thenReturn(Optional.of(intent));

        PaymentApplicationService.WebhookOutcome outcome = service.complete(
                new PaymentApplicationService.SePayWebhook(7L, "GYMABC", "in", 499_999L, "bank-ref"), "hash", true);

        assertEquals("UNDERPAID", outcome.outcome());
        verify(outboxEventWriter, never()).write(any(), any(), any(), any(), any());
    }

    private static PaymentIntentEntity existingPayment() {
        PaymentIntentEntity payment = new PaymentIntentEntity();
        payment.setId("payment-1");
        payment.setUserId("user-1");
        payment.setGymId("gym-1");
        payment.setReferenceId("purchase-1");
        payment.setPaymentType(PaymentType.PAYMENT_TYPE_MEMBERSHIP);
        payment.setProvider("SEPAY");
        payment.setIntentAmountVnd(500_000L);
        payment.setPaymentCode("GYMABC");
        payment.setPaymentUrl("https://payment.example");
        return payment;
    }
}
