package com.portfolio.orders.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.orders.contract.OrderCreatedEvent;
import com.portfolio.orders.notification.application.Notification;
import com.portfolio.orders.notification.application.NotificationSender;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"orders.order-created.v1", "orders.order-created.v1.dlt"})
class OrderCreatedListenerIntegrationTest {

    private static final String TOPIC = "orders.order-created.v1";
    private static final String DLT = "orders.order-created.v1.dlt";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoSpyBean
    private NotificationSender sender;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void subscribeToDeadLetterTopic() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-assert-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dltConsumer = new KafkaConsumer<>(props);
        dltConsumer.subscribe(List.of(DLT));
    }

    @AfterEach
    void close() {
        dltConsumer.close();
    }

    @Test
    void validEventIsProcessedAndNotificationRecorded() throws Exception {
        OrderCreatedEvent event = newEvent("CUST-N-1");

        send(event);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(notificationsFor(event.orderId())).isEqualTo(1));
        assertThat(processedMarkers(event.eventId())).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryIsProcessedExactlyOnce() throws Exception {
        OrderCreatedEvent event = newEvent("CUST-N-2");

        send(event);
        send(event);
        send(event);

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> verify(sender, atLeast(1)).send(argThat(n -> n.orderId().equals(event.orderId()))));
        // Give the redeliveries time to be consumed, then confirm there is still only one side effect.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(notificationsFor(event.orderId())).isEqualTo(1));
        verify(sender, times(1)).send(argThat(n -> n.orderId().equals(event.orderId())));
    }

    @Test
    void malformedPayloadGoesStraightToDeadLetterTopicWithoutRetries() {
        String key = "poison-" + UUID.randomUUID();

        kafkaTemplate.send(TOPIC, key, "{this is not json");

        ConsumerRecord<String, String> dead = awaitDeadLetter(key, Duration.ofSeconds(20))
                .orElseThrow(() -> new AssertionError("Poison message was not dead-lettered"));
        assertThat(dead.value()).isEqualTo("{this is not json");
        // Depending on wrapping, the classified exception is reported as the exception or its cause.
        assertThat(Arrays.asList(header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN),
                header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)))
                .contains(InvalidEventException.class.getName());
        verify(sender, never()).send(argThat(n -> key.equals(String.valueOf(n.orderId()))));
    }

    @Test
    void transientFailureIsRetriedThenDeadLetteredAndRolledBack() throws Exception {
        OrderCreatedEvent event = newEvent("CUST-N-FAIL");
        doThrow(new IllegalStateException("simulated provider outage"))
                .when(sender).send(argThat((Notification n) -> n != null && n.orderId().equals(event.orderId())));

        send(event);

        ConsumerRecord<String, String> dead = awaitDeadLetter(event.orderId().toString(), Duration.ofSeconds(30))
                .orElseThrow(() -> new AssertionError("Failed message was not dead-lettered"));
        assertThat(objectMapper.readValue(dead.value(), OrderCreatedEvent.class).eventId()).isEqualTo(event.eventId());
        // 1 initial attempt + 2 retries (test profile), each rolled back.
        verify(sender, times(3)).send(argThat((Notification n) -> n != null && n.orderId().equals(event.orderId())));
        assertThat(processedMarkers(event.eventId())).isZero();
        assertThat(notificationsFor(event.orderId())).isZero();
    }

    private OrderCreatedEvent newEvent(String customerId) {
        return OrderCreatedEvent.of(UUID.randomUUID(), customerId, new BigDecimal("19.99"), "USD", 1,
                Instant.parse("2025-06-01T10:00:00Z"));
    }

    private void send(OrderCreatedEvent event) throws Exception {
        kafkaTemplate.send(TOPIC, event.orderId().toString(), objectMapper.writeValueAsString(event)).get();
    }

    private int notificationsFor(UUID orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM notification_log WHERE order_id = ?",
                Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private int processedMarkers(UUID eventId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private Optional<ConsumerRecord<String, String>> awaitDeadLetter(String key, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : dltConsumer.poll(Duration.ofMillis(250))) {
                if (key.equals(record.key())) {
                    return Optional.of(record);
                }
            }
        }
        return Optional.empty();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
