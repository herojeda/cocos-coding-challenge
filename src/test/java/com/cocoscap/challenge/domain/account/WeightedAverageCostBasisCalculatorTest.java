package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeightedAverageCostBasisCalculatorTest {

    private final CostBasisCalculator calculator = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("PF-08 · Compras a distintos precios")
    void pf08BuysAtDifferentPrices() {
        CostBasisState state = apply(
                buy(10, "100"),
                buy(10, "200"));

        assertThat(state.quantity()).isEqualTo(20L);
        assertThat(state.averageCost().orElseThrow()).isEqualByComparingTo("150");
        assertThat(state.averageCost().orElseThrow().scale()).isEqualTo(6);
        assertThat(state.realizedPnl()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("PF-09 · Una venta parcial no cambia el costo promedio")
    void pf09PartialSellKeepsAverageCost() {
        CostBasisState state = apply(
                buy(10, "100"),
                buy(10, "200"),
                sell(10, "150"));

        assertThat(state.quantity()).isEqualTo(10L);
        assertThat(state.averageCost().orElseThrow()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("PF-10 · Una posición que vuelve a cero reinicia el costo")
    void pf10FlatPositionResetsAverageCost() {
        CostBasisState state = apply(
                buy(10, "100"),
                sell(10, "120"),
                buy(10, "200"));

        assertThat(state.quantity()).isEqualTo(10L);
        assertThat(state.averageCost().orElseThrow()).isEqualByComparingTo("200");
        assertThat(state.realizedPnl()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("PF-11 · Rendimiento de PAMP en la seed")
    void pf11PampAverageCostFromSeed() {
        CostBasisState state = apply(pampSeedFills());

        assertThat(state.quantity()).isEqualTo(40L);
        assertThat(state.averageCost().orElseThrow()).isEqualByComparingTo("930");
        assertThat(state.realizedPnl()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("PF-12 · Resultado realizado de una venta parcial")
    void pf12PartialSellRealizedPnl() {
        CostBasisState state = apply(
                buy(10, "100"),
                buy(10, "200"),
                sell(10, "180"));

        assertThat(state.realizedPnl()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("PF-13 · Datos complementarios de PAMP en la seed")
    void pf13PampRealizedPnlFromSeed() {
        CostBasisState state = apply(pampSeedFills());

        assertThat(state.realizedPnl()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("PF-14 · BMA negativo en la seed")
    void pf14NegativeBmaHasNoAverageCost() {
        CostBasisState state = apply(bmaSeedFills());

        assertThat(state.quantity()).isEqualTo(-10L);
        assertThat(state.averageCost()).isEmpty();
    }

    @Test
    @DisplayName("PF-16 · Venta que supera la tenencia")
    void pf16SellBeyondHoldingRealizesOnlyHeldQuantity() {
        CostBasisState state = apply(bmaSeedFills());

        assertThat(state.realizedPnl()).isEqualByComparingTo("-200.00");
    }

    @Test
    @DisplayName("PF-17 · Compra sobre una tenencia negativa")
    void pf17BuyCoveringShortThenOpensAtBuyPrice() {
        CostBasisState state = apply(
                buy(20, "1540"),
                sell(30, "1530"),
                buy(15, "1500"));

        assertThat(state.quantity()).isEqualTo(5L);
        assertThat(state.averageCost().orElseThrow()).isEqualByComparingTo("1500");
        assertThat(state.realizedPnl()).isEqualByComparingTo("-200.00");
    }

    @Test
    @DisplayName("Rounding · average cost scale 6 and realized PnL scale 2 with HALF_EVEN")
    void roundsAverageAndRealizedWithHalfEven() {
        // (1×1 + 1×1.0000005) / 2 = 1.00000025 → 1.000000; counterpart 1.00000075 → 1.000001
        assertThat(apply(buy(1, "1"), buy(1, "1.0000005")).averageCost().orElseThrow())
                .isEqualByComparingTo("1.000000")
                .extracting(BigDecimal::scale)
                .isEqualTo(6);
        assertThat(apply(buy(1, "1"), buy(1, "1.0000015")).averageCost().orElseThrow())
                .isEqualByComparingTo("1.000001");

        // (1.005 − 1) → 0.005 → 0.00; (1.015 − 1) → 0.015 → 0.02
        assertThat(apply(buy(1, "1"), sell(1, "1.005")).realizedPnl())
                .isEqualByComparingTo("0.00")
                .extracting(BigDecimal::scale)
                .isEqualTo(2);
        assertThat(apply(buy(1, "1"), sell(1, "1.015")).realizedPnl()).isEqualByComparingTo("0.02");
    }

    private CostBasisState apply(Fill... fills) {
        return apply(List.of(fills));
    }

    private CostBasisState apply(List<Fill> fills) {
        return applyFrom(CostBasisState.empty(), fills);
    }

    private CostBasisState applyFrom(CostBasisState start, List<Fill> fills) {
        CostBasisState state = start;
        for (Fill fill : fills) {
            state = fill.apply(calculator, state);
        }
        return state;
    }

    private static List<Fill> pampSeedFills() {
        return List.of(buy(50, "930"), sell(10, "940"));
    }

    private static List<Fill> bmaSeedFills() {
        return List.of(buy(20, "1540"), sell(30, "1530"));
    }

    private static Fill buy(long quantity, String price) {
        return new Fill(true, quantity, new BigDecimal(price));
    }

    private static Fill sell(long quantity, String price) {
        return new Fill(false, quantity, new BigDecimal(price));
    }

    private record Fill(boolean buy, long quantity, BigDecimal price) {
        CostBasisState apply(CostBasisCalculator calculator, CostBasisState state) {
            return buy
                    ? calculator.applyBuy(state, quantity, price)
                    : calculator.applySell(state, quantity, price);
        }
    }
}
