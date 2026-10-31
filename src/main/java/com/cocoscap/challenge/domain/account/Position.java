package com.cocoscap.challenge.domain.account;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Holdings for one instrument. Does not enforce non-negative quantity (seed short BMA / S3)
 * or available quantity — order policy does that later. Average cost and realized PnL come only
 * from {@link CostBasisCalculator}.
 */
public final class Position {

    private final InstrumentId instrumentId;
    private final CostBasisCalculator costBasisCalculator;
    private CostBasisState costBasis = CostBasisState.empty();
    private long reservedQuantity;

    Position(InstrumentId instrumentId, CostBasisCalculator costBasisCalculator) {
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.costBasisCalculator = Objects.requireNonNull(costBasisCalculator, "costBasisCalculator");
    }

    public InstrumentId instrumentId() {
        return instrumentId;
    }

    public long quantity() {
        return costBasis.quantity();
    }

    public long reservedQuantity() {
        return reservedQuantity;
    }

    public long availableQuantity() {
        return quantity() - reservedQuantity;
    }

    public Optional<BigDecimal> averageCost() {
        return costBasis.averageCost();
    }

    public BigDecimal realizedPnl() {
        return costBasis.realizedPnl();
    }

    void applyBuy(long quantity, BigDecimal price) {
        costBasis = costBasisCalculator.applyBuy(costBasis, quantity, price);
    }

    void applySell(long quantity, BigDecimal price) {
        costBasis = costBasisCalculator.applySell(costBasis, quantity, price);
    }

    void reserve(long quantity) {
        requirePositive(quantity);
        this.reservedQuantity = Math.addExact(this.reservedQuantity, quantity);
    }

    void release(long quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new IllegalStateException("reserved quantity would become negative");
        }
        this.reservedQuantity -= quantity;
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
