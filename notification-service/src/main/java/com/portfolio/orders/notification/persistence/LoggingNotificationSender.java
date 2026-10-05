package com.portfolio.orders.notification.persistence;

import com.portfolio.orders.notification.application.Notification;
import com.portfolio.orders.notification.application.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * Demo adapter: records the notification in an outbox-style table and logs it instead of
 * calling a real email/SMS provider. Swap for an SES/SNS/SendGrid adapter in a real system.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LoggingNotificationSender(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void send(Notification notification) {
        jdbc.update("""
                INSERT INTO notification_log (event_id, order_id, customer_id, channel, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, notification.eventId(), notification.orderId(), notification.customerId(),
                notification.channel(), notification.message(), Timestamp.from(clock.instant()));
        log.info("Notification queued channel={} customerId={} orderId={}", notification.channel(),
                notification.customerId(), notification.orderId());
    }
}
