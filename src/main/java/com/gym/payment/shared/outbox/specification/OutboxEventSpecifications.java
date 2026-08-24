package com.gym.payment.shared.outbox.specification;

import com.gym.payment.shared.outbox.entity.OutboxEventEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class OutboxEventSpecifications {

    private OutboxEventSpecifications() {}

    public static Specification<OutboxEventEntity> readyAt(Instant now) {
        return (root, query, cb) -> cb.and(
                root.get("status").in(OutboxEventEntity.OutboxStatus.PENDING, OutboxEventEntity.OutboxStatus.IN_FLIGHT),
                cb.lessThanOrEqualTo(root.get("nextAttemptAt"), now));
    }
}
