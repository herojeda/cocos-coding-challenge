package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Simulates the incremental path (persist rounded column values between fills) against a
 * one-shot in-memory rebuild.
 */
class CostBasisIncrementalRebuildPropertyTest {

    private final CostBasisCalculator calculator = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("Property · one-shot fold equals chunked fold with rounded intermediate state")
    void oneShotEqualsChunkedWithRoundedIntermediates() {
        List<Fill> fills = List.of(
                buy(3, "100.1234567"),
                buy(5, "200.9876543"),
                sell(2, "180.5555555"),
                buy(1, "150.1111111"),
                sell(4, "160.4444444"),
                buy(7, "140.3333333"));

        CostBasisState oneShot = apply(CostBasisState.empty(), fills);

        CostBasisState chunked = CostBasisState.empty();
        for (List<Fill> chunk : List.of(fills.subList(0, 2), fills.subList(2, 4), fills.subList(4, 6))) {
            chunked = apply(chunked, chunk);
            chunked = new CostBasisState(
                    chunked.quantity(),
                    chunked.averageCost().map(c -> c.setScale(6, RoundingMode.HALF_EVEN)),
                    chunked.realizedPnl().setScale(2, RoundingMode.HALF_EVEN));
        }

        assertThat(chunked).isEqualTo(oneShot);
    }

    private CostBasisState apply(CostBasisState start, List<Fill> fills) {
        CostBasisState state = start;
        for (Fill fill : fills) {
            state = fill.buy
                    ? calculator.applyBuy(state, fill.quantity, fill.price)
                    : calculator.applySell(state, fill.quantity, fill.price);
        }
        return state;
    }

    private static Fill buy(long quantity, String price) {
        return new Fill(true, quantity, new BigDecimal(price));
    }

    private static Fill sell(long quantity, String price) {
        return new Fill(false, quantity, new BigDecimal(price));
    }

    private record Fill(boolean buy, long quantity, BigDecimal price) {}
}
