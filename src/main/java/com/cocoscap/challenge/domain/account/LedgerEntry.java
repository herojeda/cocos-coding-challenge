package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only projection of a ledger order used to rebuild an {@link Account}.
 * Not the {@code Order} entity — that belongs to order-placement.
 *
 * <p>{@code price} is optional: a MARKET order rejected for missing quote (ORD-25 / S5) is stored
 * without a price.
 */
public record LedgerEntry(
        OrderId id,
        InstrumentId instrumentId,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        BigDecimal size,
        BigDecimal price,
        Instant datetime) {

    public LedgerEntry {
        id = requireNonNull(id, "id");
        instrumentId = requireNonNull(instrumentId, "instrumentId");
        side = requireNonNull(side, "side");
        type = requireNonNull(type, "type");
        status = requireNonNull(status, "status");
        size = requireNonNull(size, "size");
        datetime = requireNonNull(datetime, "datetime");
        requirePositive(size, "size");
        if (price != null) {
            requirePositive(price, "price");
        }
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
