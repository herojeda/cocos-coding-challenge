package com.cocoscap.challenge.domain.portfolio;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One non-zero holding in a valued portfolio. Market value, total return and daily change are empty
 * when there is no quote; average cost and total return are also empty for non-positive holdings.
 */
public record PortfolioPosition(
        InstrumentId instrumentId,
        String ticker,
        String name,
        long quantity,
        Optional<BigDecimal> marketValue,
        Optional<BigDecimal> totalReturn,
        Optional<BigDecimal> averageCost,
        BigDecimal realizedPnl,
        Optional<BigDecimal> dailyChange) {

    public PortfolioPosition {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(marketValue, "marketValue");
        Objects.requireNonNull(totalReturn, "totalReturn");
        Objects.requireNonNull(averageCost, "averageCost");
        Objects.requireNonNull(realizedPnl, "realizedPnl");
        Objects.requireNonNull(dailyChange, "dailyChange");
    }
}
