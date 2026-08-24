package com.gym.payment.shared.outbox.scheduler;

import com.google.protobuf.Message;
import com.gym.common.kafka.producer.EventPublisher;
import com.gym.payment.config.PaymentServiceProperties;
import com.gym.payment.shared.outbox.entity.OutboxEventEntity;
import com.gym.payment.shared.outbox.service.OutboxPayloadParser;
import com.gym.payment.shared.outbox.service.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxRelayService outboxRelayService;
    private final EventPublisher eventPublisher;
    private final OutboxPayloadParser payloadParser;
    private final PaymentServiceProperties properties;

    @Scheduled(fixedDelayString = "${app.payment.outbox.poll-delay:PT2S}")
    public void processOutboxEvents() {
        List<OutboxEventEntity> pending = outboxRelayService.claimBatch(
                properties.outbox().batchSize(),
                properties.outbox().leaseDuration()
        );
        for (OutboxEventEntity event : pending) {
            publish(event);
        }
    }

    protected void publish(OutboxEventEntity event) {
        try {
            Message payload = payloadParser.parse(event.getPayloadType(), event.getEventType(), event.getPayload());
            String key = event.getKafkaKey() != null && !event.getKafkaKey().isBlank()
                    ? event.getKafkaKey()
                    : event.getAggregateId();
            eventPublisher.publish(event.getTopic(), key, payload, event.getId().toString(), Map.of());
            outboxRelayService.markPublished(event.getId());
        } catch (Exception e) {
            outboxRelayService.markFailedOrRetry(event.getId(), e);
        }
    }
}
