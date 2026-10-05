package com.portfolio.orders.service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.orders.contract.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges committed domain events to Kafka. Records are keyed by orderId so all events for
 * one order land on the same partition and keep their relative ordering.
 *
 * <p>Trade-off: publishing after commit can lose an event if the process dies between the
 * commit and the send. A transactional outbox removes that gap (see README, future work).
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                               @Value("${app.kafka.topics.order-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        String payload = serialize(event);
        kafkaTemplate.send(topic, event.orderId().toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreated eventId={} orderId={}", event.eventId(), event.orderId(), ex);
                    } else {
                        log.info("Published OrderCreated eventId={} orderId={} partition={} offset={}", event.eventId(),
                                event.orderId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }

    private String serialize(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize OrderCreated event " + event.eventId(), e);
        }
    }
}
