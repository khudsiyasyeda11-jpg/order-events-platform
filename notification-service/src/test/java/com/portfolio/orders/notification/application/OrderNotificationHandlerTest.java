package com.portfolio.orders.notification.application;

import com.portfolio.orders.contract.OrderCreatedEvent;
import com.portfolio.orders.notification.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderNotificationHandlerTest {

    private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final OrderNotificationHandler handler = new OrderNotificationHandler(processedEvents, sender);

    private final OrderCreatedEvent event = OrderCreatedEvent.of(UUID.randomUUID(), "CUST-7",
            new BigDecimal("42.00"), "USD", 3, Instant.parse("2025-04-01T08:00:00Z"));

    @Test
    void firstDeliverySendsNotification() {
        when(processedEvents.markProcessed(event.eventId(), event.orderId())).thenReturn(true);

        assertThat(handler.handle(event)).isTrue();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().customerId()).isEqualTo("CUST-7");
        assertThat(captor.getValue().message()).contains("42.00 USD").contains("3 item(s)");
    }

    @Test
    void duplicateDeliveryIsSkipped() {
        when(processedEvents.markProcessed(event.eventId(), event.orderId())).thenReturn(false);

        assertThat(handler.handle(event)).isFalse();

        verify(sender, never()).send(any());
    }
}
