package com.portfolio.orders.service.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order " + orderId + " was not found");
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
