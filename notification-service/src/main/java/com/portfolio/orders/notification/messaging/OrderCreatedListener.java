package com.portfolio.orders.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.orders.contract.OrderCreatedEvent;
import com.portfolio.orders.notification.application.OrderNotificationHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes OrderCreated events. Failure semantics (configured in
 * {@link com.portfolio.orders.notification.config.KafkaConsumerConfig}):
 * <ul>
 *   <li>{@link InvalidEventException}: sent straight to the dead-letter topic, no retries.</li>
 *   <li>Any other exception: retried with back-off, then dead-lettered.</li>
 *   <li>Duplicate deliveries: detected by eventId and acknowledged without side effects.</li>
 * </ul>
 */
@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final ObjectMapper objectMapper;
    private final OrderNotificationHandler handler;

    public OrderCreatedListener(ObjectMapper objectMapper, OrderNotificationHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @KafkaListener(id = "order-created-listener",
            topics = "${app.kafka.topics.order-created}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        OrderCreatedEvent event = parse(record);
        MDC.put("eventId", String.valueOf(event.eventId()));
        MDC.put("orderId", String.valueOf(event.orderId()));
        try {
            log.info("Received OrderCreated partition={} offset={}", record.partition(), record.offset());
            handler.handle(event);
        } finally {
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }

    private OrderCreatedEvent parse(ConsumerRecord<String, String> record) {
        if (record.value() == null || record.value().isBlank()) {
            throw new InvalidEventException("Empty payload at offset " + record.offset());
        }
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        } catch (JsonProcessingException e) {
            throw new InvalidEventException("Malformed OrderCreated payload at offset " + record.offset(), e);
        }
        if (event.eventId() == null || event.orderId() == null || event.customerId() == null) {
            throw new InvalidEventException("OrderCreated payload is missing required fields at offset " + record.offset());
        }
        return event;
    }
}
