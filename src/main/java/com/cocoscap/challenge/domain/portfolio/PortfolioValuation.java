package com.cocoscap.challenge.domain.portfolio;

import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountState.PositionState;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.market.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure portfolio valuation: cash plus market value of non-zero holdings at the quotes supplied by
 * the caller. Does not read or write persistence. Positions are ordered by ticker for a stable
 * list; that order is not a business rule.
 */
public final class PortfolioValuation {

    public static final int PERCENTAGE_SCALE = 2;

    public Portfolio value(
            Account account,
            Map<InstrumentId, Instrument> instruments,
            Map<InstrumentId, Quote> quotes) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(instruments, "instruments");
        Objects.requireNonNull(quotes, "quotes");

        List<PortfolioPosition> positions = account.state().positions().stream()
                .filter(position -> position.quantity() != 0L)
                .map(position -> toPortfolioPosition(position, instruments, quotes))
                .sorted(Comparator.comparing(PortfolioPosition::ticker))
                .toList();

        BigDecimal holdingsValue = positions.stream()
                .map(PortfolioPosition::marketValue)
                .flatMap(Optional::stream)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Portfolio(account.cash().add(holdingsValue), account.availableCash(), positions);
    }

    private static PortfolioPosition toPortfolioPosition(
            PositionState position,
            Map<InstrumentId, Instrument> instruments,
            Map<InstrumentId, Quote> quotes) {
        Instrument instrument = instruments.get(position.instrumentId());
        if (instrument == null) {
            throw new IllegalArgumentException("missing instrument for " + position.instrumentId());
        }

        Quote quote = quotes.get(position.instrumentId());
        Optional<BigDecimal> marketValue = quote == null
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(position.quantity()).multiply(quote.close()));

        Optional<BigDecimal> averageCost = position.averageCost();
        Optional<BigDecimal> totalReturn = Optional.empty();
        Optional<BigDecimal> dailyChange = Optional.empty();
        if (quote != null) {
            dailyChange = Optional.of(percentChange(quote.close(), quote.previousClose()));
            if (averageCost.isPresent()) {
                totalReturn = Optional.of(percentChange(quote.close(), averageCost.get()));
            }
        }

        return new PortfolioPosition(
                instrument.id(),
                instrument.ticker(),
                instrument.name(),
                position.quantity(),
                marketValue,
                totalReturn,
                averageCost,
                position.realizedPnl(),
                dailyChange);
    }

    /**
     * Percentage points with a single {@link RoundingMode#HALF_EVEN} rounding:
     * {@code (numerator − denominator) × 100 / denominator}.
     */
    private static BigDecimal percentChange(BigDecimal numerator, BigDecimal denominator) {
        return numerator
                .subtract(denominator)
                .multiply(BigDecimal.valueOf(100))
                .divide(denominator, PERCENTAGE_SCALE, RoundingMode.HALF_EVEN);
    }
}
