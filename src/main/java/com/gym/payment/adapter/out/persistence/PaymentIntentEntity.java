package com.gym.payment.adapter.out.persistence;

import com.gym.proto.common.v1.PaymentType;
import com.gym.proto.payment.v1.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents")
@Getter
@Setter
@NoArgsConstructor
public class PaymentIntentEntity {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "gym_id", nullable = false)
    private String gymId;

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Column(nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PAYMENT_STATUS_PENDING;

    @Column(name = "intent_amount_vnd", nullable = false)
    private long intentAmountVnd;

    @Column(name = "received_amount_vnd")
    private Long receivedAmountVnd;

    @Column(name = "payment_code", nullable = false, unique = true)
    private String paymentCode;

    @Column(name = "payment_url", nullable = false, columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "provider_transaction_id", unique = true)
    private Long providerTransactionId;

    @Column(name = "provider_reference_code")
    private String providerReferenceCode;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
