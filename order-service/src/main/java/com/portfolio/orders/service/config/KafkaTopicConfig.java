package com.portfolio.orders.service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic orderCreatedTopic(@Value("${app.kafka.topics.order-created}") String name,
                               @Value("${app.kafka.topics.partitions:3}") int partitions,
                               @Value("${app.kafka.topics.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
