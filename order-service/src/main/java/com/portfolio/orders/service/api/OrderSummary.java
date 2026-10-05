package com.portfolio.orders.service.api;

import com.portfolio.orders.service.domain.Order;
import com.portfolio.orders.service.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lightweight projection for list endpoints (no line items, avoids N+1 loads). */
public record OrderSummary(UUID id, String customerId, OrderStatus status, String currency,
                           BigDecimal totalAmount, Instant createdAt) {

    public static OrderSummary from(Order order) {
        return new OrderSummary(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getCurrency(), order.getTotalAmount(), order.getCreatedAt());
    }
}
