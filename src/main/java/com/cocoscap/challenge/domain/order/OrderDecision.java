package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of placing an order <em>before</em> it is inserted into the ledger (ADR-006): status,
 * size, price, amount and rejection reason. The use case inserts from this decision, then builds
 * the registered {@link Order} with the returned id via {@link #toOrder(OrderId, Instant)}.
 */
public record OrderDecision(
        UserId userId,
        InstrumentId instrumentId,
        OrderSide side,
        OrderType type,
        Long size,
        BigDecimal price,
        BigDecimal amount,
        OrderStatus status,
        RejectionReason rejectionReason) {

    public OrderDecision {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (side != OrderSide.BUY && side != OrderSide.SELL) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        if (status == OrderStatus.REJECTED) {
            Objects.requireNonNull(rejectionReason, "rejectionReason");
        } else if (rejectionReason != null) {
            throw new IllegalArgumentException("rejectionReason is only allowed when REJECTED");
        }
        if (rejectionReason == RejectionReason.NO_MARKET_PRICE) {
            if (type != OrderType.MARKET || price != null) {
                throw new IllegalArgumentException(
                        "NO_MARKET_PRICE applies only to MARKET decisions without a price");
            }
        }
        if (size == null) {
            if (status != OrderStatus.REJECTED
                    || rejectionReason != RejectionReason.NO_MARKET_PRICE
                    || amount == null) {
                throw new IllegalArgumentException(
                        "size may be null only for NO_MARKET_PRICE rejections with a requested amount");
            }
        } else if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (price != null && price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (amount != null && amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static OrderDecision filled(
            UserId userId,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            long size,
            BigDecimal price,
            BigDecimal amount) {
        Objects.requireNonNull(price, "price");
        return new OrderDecision(
                userId, instrumentId, side, type, size, price, amount, OrderStatus.FILLED, null);
    }

    public static OrderDecision accepted(
            UserId userId,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            long size,
            BigDecimal price,
            BigDecimal amount) {
        Objects.requireNonNull(price, "price");
        if (type != OrderType.LIMIT) {
            throw new IllegalArgumentException("accepted orders must be LIMIT");
        }
        return new OrderDecision(
                userId, instrumentId, side, type, size, price, amount, OrderStatus.NEW, null);
    }

    public static OrderDecision rejected(
            UserId userId,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            Long size,
            BigDecimal price,
            BigDecimal amount,
            RejectionReason rejectionReason) {
        return new OrderDecision(
                userId,
                instrumentId,
                side,
                type,
                size,
                price,
                amount,
                OrderStatus.REJECTED,
                rejectionReason);
    }

    /** Registered ledger order after the insert returns its id. */
    public Order toOrder(OrderId id, Instant datetime) {
        return new Order(
                id,
                userId,
                instrumentId,
                side,
                type,
                size,
                price,
                amount,
                status,
                rejectionReason,
                datetime);
    }
}
