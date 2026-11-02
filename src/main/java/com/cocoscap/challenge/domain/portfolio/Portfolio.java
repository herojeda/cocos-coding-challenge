package com.cocoscap.challenge.domain.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Valued snapshot of an account: total value, available cash and non-zero positions. */
public record Portfolio(BigDecimal totalValue, BigDecimal availableCash, List<PortfolioPosition> positions) {

    public Portfolio {
        Objects.requireNonNull(totalValue, "totalValue");
        Objects.requireNonNull(availableCash, "availableCash");
        Objects.requireNonNull(positions, "positions");
        positions = List.copyOf(positions);
    }
}
