package com.portfolio.orders.service.application;

import com.portfolio.orders.contract.OrderCreatedEvent;
import com.portfolio.orders.service.api.CreateOrderRequest;
import com.portfolio.orders.service.api.OrderResponse;
import com.portfolio.orders.service.api.OrderSummary;
import com.portfolio.orders.service.domain.Order;
import com.portfolio.orders.service.domain.OrderNotFoundException;
import com.portfolio.orders.service.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Persists the order and raises an in-process event. The Kafka publication happens in
     * {@link com.portfolio.orders.service.messaging.OrderEventPublisher} only after the
     * transaction commits, so consumers never see events for rolled-back orders.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Order order = Order.create(request.customerId(), request.currency(), clock.instant());
        request.items().forEach(item -> order.addItem(item.sku(), item.quantity(), item.unitPrice()));
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(OrderCreatedEvent.of(saved.getId(), saved.getCustomerId(),
                saved.getTotalAmount(), saved.getCurrency(), saved.getItems().size(), saved.getCreatedAt()));

        log.info("Order created orderId={} customerId={} itemCount={} total={} {}", saved.getId(),
                saved.getCustomerId(), saved.getItems().size(), saved.getTotalAmount(), saved.getCurrency());
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return orderRepository.findWithItemsById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> listByCustomer(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(OrderSummary::from);
    }
}
