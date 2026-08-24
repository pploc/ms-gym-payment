package com.gym.payment.adapter.out.persistence;

import com.gym.proto.common.v1.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentIntentJpaRepository extends JpaRepository<PaymentIntentEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentIntentEntity> findByPaymentTypeAndReferenceId(PaymentType paymentType, String referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentIntentEntity> findByPaymentCode(String paymentCode);
}
