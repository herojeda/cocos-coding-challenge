package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Current state of an {@link Account} as a value, matching the persisted columns of V4 (cash,
 * reserved cash and per-instrument holdings). It is how an account enters and leaves persistence
 * (memento): it is not a historical snapshot or checkpoint of the ledger. Positions are ordered by
 * {@link InstrumentId}.
 *
 * <p>{@code cash} and {@code reservedCash} are normalized to {@link #CASH_SCALE} with
 * {@link RoundingMode#UNNECESSARY}, matching {@code NUMERIC(18,2)} so {@code equals} compares by
 * value across in-memory and database origins.
 */
public record AccountState(
        AccountId id, BigDecimal cash, BigDecimal reservedCash, List<PositionState> positions) {

    public static final int CASH_SCALE = 2;

    public AccountState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cash, "cash");
        Objects.requireNonNull(reservedCash, "reservedCash");
        Objects.requireNonNull(positions, "positions");
        cash = requireCashScale(cash, "cash");
        reservedCash = requireCashScale(reservedCash, "reservedCash");
        positions = List.copyOf(positions);
    }

    private static BigDecimal requireCashScale(BigDecimal value, String name) {
        try {
            return value.setScale(CASH_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " must have at most " + CASH_SCALE + " decimal places", ex);
        }
    }

    /** One row of the {@code positions} table for a single instrument. */
    public record PositionState(
            InstrumentId instrumentId,
            long quantity,
            long reservedQuantity,
            Optional<BigDecimal> averageCost,
            BigDecimal realizedPnl) {

        public PositionState {
            Objects.requireNonNull(instrumentId, "instrumentId");
            Objects.requireNonNull(averageCost, "averageCost");
            Objects.requireNonNull(realizedPnl, "realizedPnl");
        }
    }
}
