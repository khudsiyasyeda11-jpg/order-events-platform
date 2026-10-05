package com.portfolio.orders.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.orders.contract.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "orders.order-created.v1")
class OrderApiIntegrationTest {

    private static final String TOPIC = "orders.order-created.v1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private Consumer<String, String> consumer;

    @BeforeEach
    void subscribe() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-api-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void createOrderReturns201AndPublishesOrderCreatedEvent() throws Exception {
        String body = """
                {"customerId":"CUST-IT-1","currency":"USD",
                 "items":[{"sku":"SKU-KB-001","quantity":2,"unitPrice":49.99},
                          {"sku":"SKU-MS-002","quantity":1,"unitPrice":10.02}]}
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "it-corr-1")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(header().string("X-Correlation-Id", "it-corr-1"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(110.00))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn();

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        String orderId = created.get("id").asText();
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/orders/" + orderId);

        ConsumerRecord<String, String> record = awaitRecordWithKey(orderId, Duration.ofSeconds(20))
                .orElseThrow(() -> new AssertionError("No OrderCreated event published for " + orderId));
        OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        assertThat(event.orderId()).hasToString(orderId);
        assertThat(event.customerId()).isEqualTo("CUST-IT-1");
        assertThat(event.itemCount()).isEqualTo(2);
        assertThat(event.totalAmount()).isEqualByComparingTo("110.00");
        assertThat(event.eventId()).isNotNull();
    }

    @Test
    void getReturnsPersistedOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", "0b6f1d2e-1111-4c1a-9a01-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-1001"))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void listByCustomerReturnsPagedSummaries() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("customerId", "CUST-2002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].currency").value("EUR"));
    }

    @Test
    void unknownOrderReturns404ProblemDetail() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/orders/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type", endsWith("/order-not-found")))
                .andExpect(jsonPath("$.orderId").value(missing.toString()));
    }

    @Test
    void invalidPayloadReturns400WithFieldErrors() throws Exception {
        String body = """
                {"customerId":"","currency":"usd","items":[{"sku":"","quantity":0,"unitPrice":-1}]}
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors[?(@.field == 'currency')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')]").exists());
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidPageSizeIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("customerId", "CUST-1001").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    private Optional<ConsumerRecord<String, String>> awaitRecordWithKey(String key, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                if (key.equals(record.key())) {
                    return Optional.of(record);
                }
            }
        }
        return Optional.empty();
    }
}
