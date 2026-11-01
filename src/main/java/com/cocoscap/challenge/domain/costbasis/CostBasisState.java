package com.cocoscap.challenge.domain.costbasis;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Quantity, average cost (present iff quantity &gt; 0) and realized PnL for one instrument. */
public record CostBasisState(
        long quantity, Optional<BigDecimal> averageCost, BigDecimal realizedPnl) {

    public static final int AVERAGE_COST_SCALE = 6;
    public static final int REALIZED_PNL_SCALE = 2;

    public CostBasisState {
        Objects.requireNonNull(averageCost, "averageCost");
        Objects.requireNonNull(realizedPnl, "realizedPnl");
        if (averageCost.isPresent() != quantity > 0) {
            throw new IllegalArgumentException(
                    "averageCost must be present if and only if quantity is positive");
        }
        averageCost.ifPresent(cost -> requireScale(cost, AVERAGE_COST_SCALE, "averageCost"));
        requireScale(realizedPnl, REALIZED_PNL_SCALE, "realizedPnl");
    }

    public static CostBasisState empty() {
        return new CostBasisState(0L, Optional.empty(), BigDecimal.ZERO.setScale(REALIZED_PNL_SCALE));
    }

    private static void requireScale(BigDecimal value, int scale, String name) {
        if (value.scale() != scale) {
            throw new IllegalArgumentException(name + " must have scale " + scale);
        }
    }
}
