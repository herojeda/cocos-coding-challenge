package com.cocoscap.challenge.adapter.out.persistence.account;

import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Streams a user's orders with a bounded JDBC fetch size, ordered by {@code (datetime, id)} to use
 * {@code orders_user_id_datetime_id_idx}. Requires an active transaction: without one, pgJDBC
 * ignores the fetch size and loads the full result set.
 */
public class JdbcAccountLedgerAdapter implements AccountLedgerPort {

    private final JdbcClient jdbcClient;
    private final int fetchSize;

    public JdbcAccountLedgerAdapter(JdbcClient jdbcClient, int fetchSize) {
        if (fetchSize <= 0) {
            throw new IllegalArgumentException("fetchSize must be positive");
        }
        this.jdbcClient = jdbcClient;
        this.fetchSize = fetchSize;
    }

    @Override
    public Stream<LedgerEntry> streamByUserId(UserId userId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "ledger stream requires an active transaction: without it the driver ignores the fetch size");
        }
        return jdbcClient
                .sql(
                        """
                        SELECT id, instrument_id, side, type, status, size, price, datetime
                        FROM orders
                        WHERE user_id = :userId
                        ORDER BY datetime, id
                        """)
                .param("userId", userId.value())
                .withFetchSize(fetchSize)
                .query(OrderRow.class)
                .stream()
                .map(JdbcAccountLedgerAdapter::toDomain);
    }

    private static LedgerEntry toDomain(OrderRow row) {
        return new LedgerEntry(
                OrderId.of(row.id()),
                InstrumentId.of(row.instrumentId()),
                OrderSide.valueOf(row.side()),
                OrderType.valueOf(row.type()),
                OrderStatus.valueOf(row.status()),
                row.size(),
                row.price(),
                row.datetime().toInstant());
    }

    record OrderRow(
            long id,
            long instrumentId,
            String side,
            String type,
            String status,
            BigDecimal size,
            BigDecimal price,
            OffsetDateTime datetime) {}
}
