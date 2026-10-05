package com.portfolio.orders.service.repository;

import com.portfolio.orders.service.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real Flyway migrations against H2 (PostgreSQL mode) and checks the JPA mapping. */
@DataJpaTest(properties = "spring.kafka.bootstrap-servers=localhost:0")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void seedDataIsLoadedByFlyway() {
        var page = repository.findByCustomerId("CUST-1001", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void persistsAndReloadsOrderWithItems() {
        Order order = Order.create("CUST-REPO-1", "USD", Instant.parse("2025-05-05T05:05:05Z"));
        order.addItem("SKU-X", 2, new BigDecimal("7.50"));
        UUID id = repository.saveAndFlush(order).getId();
        entityManager.clear();

        Order reloaded = repository.findWithItemsById(id).orElseThrow();
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("15.00");
        assertThat(reloaded.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getSku()).isEqualTo("SKU-X"));
        assertThat(reloaded.getCreatedAt()).isEqualTo(Instant.parse("2025-05-05T05:05:05Z"));
    }
}
