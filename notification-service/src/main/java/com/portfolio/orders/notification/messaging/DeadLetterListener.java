package com.portfolio.orders.notification.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Surfaces dead-lettered messages in logs/metrics so on-call can triage them. Replays are a
 * deliberate, manual operation (see README runbook) rather than automatic.
 */
@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    @KafkaListener(id = "order-created-dlt-listener",
            topics = "${app.kafka.topics.order-created-dlt}",
            groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        log.warn("Dead-lettered OrderCreated key={} offset={} exception={} message={}",
                record.key(), record.offset(),
                header(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "n/a" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
