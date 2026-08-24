package com.gym.payment.shared.outbox.service;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.gym.payment.shared.outbox.entity.OutboxEventEntity;
import com.gym.payment.shared.outbox.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventWriter {

    private static final JsonFormat.Printer JSON_PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();

    private final OutboxEventJpaRepository repository;
    private final Clock clock;

    public UUID write(String aggregateType, String aggregateId, String topic, Message payload) {
        return write(aggregateType, aggregateId, topic, payload, null);
    }

    public UUID write(String aggregateType, String aggregateId, String topic, Message payload, String dedupeKey) {
        try {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now(clock);
            OutboxEventEntity event = new OutboxEventEntity();
            event.setId(eventId);
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(payload.getClass().getSimpleName());
            event.setDedupeKey(dedupeKey);
            event.setPayloadType(payload.getDescriptorForType().getFullName());
            event.setTopic(topic);
            event.setPayload(JSON_PRINTER.print(payload));
            event.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
            event.setCreatedAt(now);
            event.setNextAttemptAt(now);
            repository.save(event);
            return eventId;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize outbound event", e);
        }
    }
}
