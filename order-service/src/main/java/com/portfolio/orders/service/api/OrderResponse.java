package com.portfolio.orders.service.api;

import com.portfolio.orders.service.domain.Order;
import com.portfolio.orders.service.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt,
        List<Item> items) {

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.getSku(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(),
                order.getCurrency(), order.getTotalAmount(), order.getCreatedAt(), items);
    }
}
