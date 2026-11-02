package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Registered ledger order: always has an {@link OrderId}. Placement produces an
 * {@link OrderDecision} first; after insert, {@link OrderDecision#toOrder(OrderId, Instant)}
 * builds this type. Factories below are for tests and reconstruction of already-persisted rows.
 */
public record Order(
        OrderId id,
        UserId userId,
        InstrumentId instrumentId,
        OrderSide side,
        OrderType type,
        Long size,
        BigDecimal price,
        BigDecimal amount,
        OrderStatus status,
        RejectionReason rejectionReason,
        Instant datetime) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(datetime, "datetime");
        // Reuse OrderDecision invariants for the business fields.
        new OrderDecision(
                userId, instrumentId, side, type, size, price, amount, status, rejectionReason);
    }

    public static Order filled(
            OrderId id,
            UserId userId,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            long size,
            BigDecimal price,
            BigDecimal amount,
            Instant datetime) {
        return OrderDecision.filled(userId, instrumentId, side, type, size, price, amount)
                .toOrder(id, datetime);
    }

    public static Order accepted(
            OrderId id,
            UserId userId,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            long size,
            BigDecimal price,
            BigDecimal amount,
            Instant datetime) {
        return OrderDecision.accepted(userId, instrumentId, side, type, size, price, amount)
                .toOrder(id, datetime);
    }

    public static Order rejected(
            OrderId id,
            UserId userId,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            Long size,
            BigDecimal price,
            BigDecimal amount,
            RejectionReason rejectionReason,
            Instant datetime) {
        return OrderDecision.rejected(
                        userId, instrumentId, side, type, size, price, amount, rejectionReason)
                .toOrder(id, datetime);
    }
}
