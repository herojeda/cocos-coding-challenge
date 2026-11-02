package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable photo of an {@link Account} matching the persisted columns of V4 (cash, reserved cash
 * and per-instrument holdings). Positions are ordered by {@link InstrumentId}.
 */
public record AccountSnapshot(
        AccountId id, BigDecimal cash, BigDecimal reservedCash, List<PositionSnapshot> positions) {

    public AccountSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cash, "cash");
        Objects.requireNonNull(reservedCash, "reservedCash");
        Objects.requireNonNull(positions, "positions");
        positions = List.copyOf(positions);
    }

    /** One row of the {@code positions} table for a single instrument. */
    public record PositionSnapshot(
            InstrumentId instrumentId,
            long quantity,
            long reservedQuantity,
            Optional<BigDecimal> averageCost,
            BigDecimal realizedPnl) {

        public PositionSnapshot {
            Objects.requireNonNull(instrumentId, "instrumentId");
            Objects.requireNonNull(averageCost, "averageCost");
            Objects.requireNonNull(realizedPnl, "realizedPnl");
        }
    }
}
