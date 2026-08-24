package com.gym.payment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sepay_webhook_receipts")
@Getter
@Setter
@NoArgsConstructor
public class SePayWebhookReceiptEntity {

    @Id
    @Column(name = "provider_transaction_id")
    private long providerTransactionId;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "payment_code")
    private String paymentCode;

    @Column(name = "transfer_type", nullable = false)
    private String transferType;

    @Column(name = "transfer_amount_vnd", nullable = false)
    private long transferAmountVnd;

    @Column(name = "reference_code")
    private String referenceCode;

    @Column(name = "payload_sha256", nullable = false)
    private String payloadSha256;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
