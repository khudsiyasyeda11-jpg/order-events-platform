package com.portfolio.orders.service.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order aggregate root. Totals are always derived from line items.
 * The JPA entity name avoids the reserved word {@code ORDER} in generated JPQL.
 */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Optimistic locking; a null version also tells Spring Data the entity is new. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // for JPA
    }

    private Order(UUID id, String customerId, String currency, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.currency = currency;
        this.createdAt = createdAt;
        this.status = OrderStatus.CREATED;
        this.totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public static Order create(String customerId, String currency, Instant now) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(currency, "currency");
        return new Order(UUID.randomUUID(), customerId, currency, now);
    }

    public void addItem(String sku, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
        items.add(new OrderItem(this, sku, quantity, unitPrice));
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
