package com.portfolio.orders.notification.application;

import com.portfolio.orders.contract.OrderCreatedEvent;
import com.portfolio.orders.notification.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer: the processed-event marker and the notification record are written in
 * the same database transaction. If sending fails the transaction rolls back, the marker is
 * discarded and the message is retried. If Kafka redelivers an already-processed event, the
 * marker insert is a no-op and the event is skipped.
 */
@Service
public class OrderNotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationHandler.class);

    private final ProcessedEventRepository processedEvents;
    private final NotificationSender sender;

    public OrderNotificationHandler(ProcessedEventRepository processedEvents, NotificationSender sender) {
        this.processedEvents = processedEvents;
        this.sender = sender;
    }

    /** @return {@code true} if the event was processed, {@code false} if it was a duplicate */
    @Transactional
    public boolean handle(OrderCreatedEvent event) {
        if (!processedEvents.markProcessed(event.eventId(), event.orderId())) {
            log.info("Skipping duplicate OrderCreated eventId={}", event.eventId());
            return false;
        }
        String message = "Thanks for your order %s. Total: %s %s (%d item(s))."
                .formatted(event.orderId(), event.totalAmount(), event.currency(), event.itemCount());
        sender.send(new Notification(event.eventId(), event.orderId(), event.customerId(), "EMAIL", message));
        return true;
    }
}
