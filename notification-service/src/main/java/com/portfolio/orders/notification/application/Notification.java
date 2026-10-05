package com.portfolio.orders.notification.application;

import java.util.UUID;

/** Channel-agnostic notification to be delivered to a customer. */
public record Notification(UUID eventId, UUID orderId, String customerId, String channel, String message) {
}
