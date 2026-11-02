package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Ledger order created only through factories for valid initial statuses: {@code FILLED},
 * {@code NEW} or {@code REJECTED} with a reason. Cancellation is out of scope here.
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
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(datetime, "datetime");
        if (side != OrderSide.BUY && side != OrderSide.SELL) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        if (status == OrderStatus.REJECTED) {
            Objects.requireNonNull(rejectionReason, "rejectionReason");
        } else if (rejectionReason != null) {
            throw new IllegalArgumentException("rejectionReason is only allowed when REJECTED");
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
        Objects.requireNonNull(price, "price");
        return new Order(
                id, userId, instrumentId, side, type, size, price, amount, OrderStatus.FILLED, null, datetime);
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
        Objects.requireNonNull(price, "price");
        if (type != OrderType.LIMIT) {
            throw new IllegalArgumentException("accepted orders must be LIMIT");
        }
        return new Order(
                id, userId, instrumentId, side, type, size, price, amount, OrderStatus.NEW, null, datetime);
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
        return new Order(
                id,
                userId,
                instrumentId,
                side,
                type,
                size,
                price,
                amount,
                OrderStatus.REJECTED,
                rejectionReason,
                datetime);
    }
}
