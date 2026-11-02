package com.cocoscap.challenge.adapter.out.persistence.order;

import com.cocoscap.challenge.application.order.OrderBookPort;
import com.cocoscap.challenge.application.order.StoredOrder;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.IdempotencyKey;
import com.cocoscap.challenge.domain.order.Order;
import com.cocoscap.challenge.domain.order.OrderDecision;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import com.cocoscap.challenge.domain.order.RejectionReason;
import com.cocoscap.challenge.domain.order.RequestHash;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcOrderBookAdapter implements OrderBookPort {

    private final JdbcClient jdbcClient;

    public JdbcOrderBookAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public OrderId register(
            OrderDecision decision, IdempotencyKey idempotencyKey, RequestHash requestHash, Instant datetime) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(datetime, "datetime");

        Long id = jdbcClient
                .sql(
                        """
                        INSERT INTO orders (
                            instrument_id, user_id, size, amount, price, type, side, status, datetime,
                            rejection_reason, idempotency_key, request_hash
                        ) VALUES (
                            :instrumentId, :userId, :size, :amount, :price, :type, :side, :status, :datetime,
                            :rejectionReason, :idempotencyKey, :requestHash
                        )
                        RETURNING id
                        """)
                .param("instrumentId", decision.instrumentId().value())
                .param("userId", decision.userId().value())
                .param("size", decision.size() == null ? null : BigDecimal.valueOf(decision.size()))
                .param("amount", decision.amount())
                .param("price", decision.price())
                .param("type", decision.type().name())
                .param("side", decision.side().name())
                .param("status", decision.status().name())
                .param("datetime", OffsetDateTime.ofInstant(datetime, ZoneOffset.UTC))
                .param(
                        "rejectionReason",
                        decision.rejectionReason() == null ? null : decision.rejectionReason().name())
                .param("idempotencyKey", idempotencyKey.value())
                .param("requestHash", requestHash.value())
                .query(Long.class)
                .single();
        return OrderId.of(id);
    }

    @Override
    public Optional<StoredOrder> findByUserAndKey(UserId userId, IdempotencyKey idempotencyKey) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");

        return jdbcClient
                .sql(
                        """
                        SELECT id, user_id, instrument_id, side, type, size, amount, price, status,
                               rejection_reason, datetime, request_hash
                        FROM orders
                        WHERE user_id = :userId AND idempotency_key = :idempotencyKey
                        """)
                .param("userId", userId.value())
                .param("idempotencyKey", idempotencyKey.value())
                .query(OrderRow.class)
                .optional()
                .map(JdbcOrderBookAdapter::toStoredOrder);
    }

    private static StoredOrder toStoredOrder(OrderRow row) {
        Order order = new Order(
                OrderId.of(row.id()),
                UserId.of(row.userId()),
                InstrumentId.of(row.instrumentId()),
                OrderSide.valueOf(row.side()),
                OrderType.valueOf(row.type()),
                toShareSize(row.size()),
                row.price(),
                row.amount(),
                OrderStatus.valueOf(row.status()),
                row.rejectionReason() == null ? null : RejectionReason.valueOf(row.rejectionReason()),
                row.datetime().toInstant());
        return new StoredOrder(order, RequestHash.of(row.requestHash()));
    }

    private static Long toShareSize(BigDecimal size) {
        if (size == null) {
            return null;
        }
        return size.longValueExact();
    }

    record OrderRow(
            long id,
            long userId,
            long instrumentId,
            String side,
            String type,
            BigDecimal size,
            BigDecimal amount,
            BigDecimal price,
            String status,
            String rejectionReason,
            OffsetDateTime datetime,
            String requestHash) {}
}
