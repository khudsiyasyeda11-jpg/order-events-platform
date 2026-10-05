package com.portfolio.orders.service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void newOrderStartsInCreatedStatusWithZeroTotal() {
        Order order = Order.create("CUST-1", "USD", Instant.parse("2025-01-01T00:00:00Z"));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getVersion()).isNull();
    }

    @Test
    void totalIsDerivedFromLineItems() {
        Order order = Order.create("CUST-1", "USD", Instant.now());
        order.addItem("SKU-A", 2, new BigDecimal("10.25"));
        order.addItem("SKU-B", 1, new BigDecimal("4.50"));

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(order.getTotalAmount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsNonPositiveQuantityOrPrice() {
        Order order = Order.create("CUST-1", "USD", Instant.now());

        assertThatThrownBy(() -> order.addItem("SKU-A", 0, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.addItem("SKU-A", 1, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemsViewIsImmutable() {
        Order order = Order.create("CUST-1", "USD", Instant.now());
        order.addItem("SKU-A", 1, BigDecimal.TEN);

        assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
