package com.portfolio.orders.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Integration event emitted by order-service after an order has been committed.
 *
 * <p>The payload is serialized as JSON without type headers so that producers and
 * consumers stay decoupled from Java class names. {@code eventId} is unique per
 * emission and is the key used by consumers for idempotent processing.
 *
 * @param eventId       unique id of this event (idempotency key)
 * @param orderId       id of the order aggregate (also used as the Kafka record key)
 * @param customerId    opaque customer reference (no personal data is carried)
 * @param totalAmount   order total
 * @param currency      ISO-4217 currency code
 * @param itemCount     number of line items
 * @param occurredAt    when the order was created
 * @param schemaVersion payload schema version for forward-compatible evolution
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency,
        int itemCount,
        Instant occurredAt,
        int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static OrderCreatedEvent of(UUID orderId, String customerId, BigDecimal totalAmount,
                                       String currency, int itemCount, Instant occurredAt) {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, customerId, totalAmount,
                currency, itemCount, occurredAt, CURRENT_SCHEMA_VERSION);
    }
}
