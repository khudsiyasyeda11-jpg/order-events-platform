package com.portfolio.orders.notification.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Repository
public class ProcessedEventRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProcessedEventRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Atomically records that an event has been processed.
     * {@code ON CONFLICT DO NOTHING} (the primary key is the only unique constraint) is safe under
     * concurrent consumers and does not abort the surrounding PostgreSQL transaction the way a
     * caught unique-violation would. The same syntax is supported by H2 in PostgreSQL mode.
     *
     * @return {@code true} if this call inserted the marker (first delivery)
     */
    public boolean markProcessed(UUID eventId, UUID orderId) {
        int inserted = jdbc.update("""
                INSERT INTO processed_events (event_id, order_id, processed_at)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, eventId, orderId, Timestamp.from(clock.instant()));
        return inserted == 1;
    }
}
