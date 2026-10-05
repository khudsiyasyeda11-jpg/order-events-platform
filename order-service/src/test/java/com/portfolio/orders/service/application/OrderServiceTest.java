package com.portfolio.orders.service.application;

import com.portfolio.orders.contract.OrderCreatedEvent;
import com.portfolio.orders.service.api.CreateOrderRequest;
import com.portfolio.orders.service.api.OrderItemRequest;
import com.portfolio.orders.service.api.OrderResponse;
import com.portfolio.orders.service.domain.Order;
import com.portfolio.orders.service.domain.OrderNotFoundException;
import com.portfolio.orders.service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2025-03-01T12:00:00Z");

    private OrderRepository repository;
    private ApplicationEventPublisher publisher;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new OrderService(repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createPersistsOrderAndRaisesOrderCreatedEvent() {
        var request = new CreateOrderRequest("CUST-42", "USD", List.of(
                new OrderItemRequest("SKU-1", 3, new BigDecimal("2.00")),
                new OrderItemRequest("SKU-2", 1, new BigDecimal("5.99"))));

        OrderResponse response = service.create(request);

        assertThat(response.totalAmount()).isEqualByComparingTo("11.99");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.items()).hasSize(2);

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        OrderCreatedEvent event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(response.id());
        assertThat(event.eventId()).isNotNull();
        assertThat(event.customerId()).isEqualTo("CUST-42");
        assertThat(event.itemCount()).isEqualTo(2);
        assertThat(event.totalAmount()).isEqualByComparingTo("11.99");
        assertThat(event.schemaVersion()).isEqualTo(OrderCreatedEvent.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void getThrowsWhenOrderIsMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findWithItemsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
