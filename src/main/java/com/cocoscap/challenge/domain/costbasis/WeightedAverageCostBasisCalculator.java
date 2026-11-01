package com.cocoscap.challenge.domain.costbasis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/** Weighted-average cost basis (ADR-005); rounds avg to 6 dp and PnL to 2 with HALF_EVEN. */
public final class WeightedAverageCostBasisCalculator implements CostBasisCalculator {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    @Override
    public CostBasisState applyBuy(CostBasisState current, long quantity, BigDecimal price) {
        Objects.requireNonNull(current, "current");
        requirePositive(quantity, "quantity");
        requirePositive(price, "price");

        long priorQty = current.quantity();
        long newQty = Math.addExact(priorQty, quantity);

        if (priorQty < 0) {
            return coverShortThenOpen(current, price, newQty);
        }
        if (priorQty == 0) {
            return new CostBasisState(newQty, Optional.of(scaleAverage(price)), current.realizedPnl());
        }
        BigDecimal weighted = BigDecimal.valueOf(priorQty)
                .multiply(current.averageCost().orElseThrow())
                .add(BigDecimal.valueOf(quantity).multiply(price));
        BigDecimal average = weighted.divide(
                BigDecimal.valueOf(newQty), CostBasisState.AVERAGE_COST_SCALE, ROUNDING);
        return new CostBasisState(newQty, Optional.of(average), current.realizedPnl());
    }

    @Override
    public CostBasisState applySell(CostBasisState current, long quantity, BigDecimal price) {
        Objects.requireNonNull(current, "current");
        requirePositive(quantity, "quantity");
        requirePositive(price, "price");

        long priorQty = current.quantity();
        long soldFromHolding = priorQty > 0 ? Math.min(quantity, priorQty) : 0L;
        BigDecimal realized = current.realizedPnl();
        if (soldFromHolding > 0) {
            BigDecimal pnl = price.subtract(current.averageCost().orElseThrow())
                    .multiply(BigDecimal.valueOf(soldFromHolding));
            realized = scaleRealized(realized.add(pnl));
        }

        long newQty = Math.subtractExact(priorQty, quantity);
        Optional<BigDecimal> average =
                newQty > 0 ? current.averageCost() : Optional.empty();
        return new CostBasisState(newQty, average, realized);
    }

    /** Cover a short with no cost/PnL; excess above zero opens at the buy price. */
    private static CostBasisState coverShortThenOpen(
            CostBasisState current, BigDecimal price, long newQty) {
        if (newQty > 0) {
            return new CostBasisState(newQty, Optional.of(scaleAverage(price)), current.realizedPnl());
        }
        return new CostBasisState(newQty, Optional.empty(), current.realizedPnl());
    }

    private static BigDecimal scaleAverage(BigDecimal value) {
        return value.setScale(CostBasisState.AVERAGE_COST_SCALE, ROUNDING);
    }

    private static BigDecimal scaleRealized(BigDecimal value) {
        return value.setScale(CostBasisState.REALIZED_PNL_SCALE, ROUNDING);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
