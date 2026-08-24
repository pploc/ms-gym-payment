package com.gym.payment.shared.outbox.repository;

import com.gym.payment.shared.outbox.entity.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID>, JpaSpecificationExecutor<OutboxEventEntity> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Page<OutboxEventEntity> findAll(Specification<OutboxEventEntity> specification, Pageable pageable);

}
