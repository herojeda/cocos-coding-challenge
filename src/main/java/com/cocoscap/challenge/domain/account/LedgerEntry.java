package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * Read-only projection of a ledger order used to rebuild an {@link Account}.
 * Not the {@code Order} entity — that belongs to order-placement.
 */
public record LedgerEntry(
        long id,
        long instrumentId,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        BigDecimal size,
        BigDecimal price,
        LocalDateTime datetime) {

    public LedgerEntry {
        side = requireNonNull(side, "side");
        type = requireNonNull(type, "type");
        status = requireNonNull(status, "status");
        size = requireNonNull(size, "size");
        price = requireNonNull(price, "price");
        datetime = requireNonNull(datetime, "datetime");
        requirePositive(size, "size");
        requirePositive(price, "price");
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
