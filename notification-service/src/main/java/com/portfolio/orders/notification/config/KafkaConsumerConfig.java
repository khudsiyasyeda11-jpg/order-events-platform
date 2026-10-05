package com.portfolio.orders.notification.config;

import com.portfolio.orders.notification.messaging.InvalidEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry + dead-letter policy. Spring Boot wires the {@link CommonErrorHandler} bean into the
 * auto-configured listener container factory.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    NewTopic orderCreatedTopic(@Value("${app.kafka.topics.order-created}") String name,
                               @Value("${app.kafka.topics.partitions:3}") int partitions,
                               @Value("${app.kafka.topics.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    /** DLT uses the same partition count so a record can keep its source partition. */
    @Bean
    NewTopic orderCreatedDeadLetterTopic(@Value("${app.kafka.topics.order-created-dlt}") String name,
                                         @Value("${app.kafka.topics.partitions:3}") int partitions,
                                         @Value("${app.kafka.topics.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations,
                                         @Value("${app.kafka.topics.order-created-dlt}") String deadLetterTopic,
                                         @Value("${app.kafka.retry.interval-ms:1000}") long intervalMs,
                                         @Value("${app.kafka.retry.max-retries:3}") long maxRetries) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, ex) -> {
                    log.error("Dead-lettering record key={} offset={} after failure: {}", record.key(),
                            record.offset(), ex.getMessage());
                    return new TopicPartition(deadLetterTopic, record.partition());
                });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervalMs, maxRetries));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
