package com.cocoscap.challenge.adapter.in.web.portfolio;

import com.cocoscap.challenge.domain.portfolio.Portfolio;
import com.cocoscap.challenge.domain.portfolio.PortfolioPosition;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.lang.Nullable;

record PortfolioResponse(BigDecimal totalValue, BigDecimal availableCash, List<PositionResponse> positions) {

    static PortfolioResponse from(Portfolio portfolio) {
        List<PositionResponse> positions =
                portfolio.positions().stream().map(PositionResponse::from).toList();
        return new PortfolioResponse(portfolio.totalValue(), portfolio.availableCash(), positions);
    }

    record PositionResponse(
            long instrumentId,
            String ticker,
            String name,
            long quantity,
            @Nullable BigDecimal marketValue,
            @Nullable BigDecimal totalReturnPct,
            @Nullable BigDecimal averageCost,
            BigDecimal realizedPnl,
            @Nullable BigDecimal dailyChangePct) {

        static PositionResponse from(PortfolioPosition position) {
            return new PositionResponse(
                    position.instrumentId().value(),
                    position.ticker(),
                    position.name(),
                    position.quantity(),
                    position.marketValue().orElse(null),
                    position.totalReturn().orElse(null),
                    position.averageCost().orElse(null),
                    position.realizedPnl(),
                    position.dailyChange().orElse(null));
        }
    }
}
