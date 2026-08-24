package com.gym.payment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SePayWebhookReceiptJpaRepository extends JpaRepository<SePayWebhookReceiptEntity, Long> {
}
