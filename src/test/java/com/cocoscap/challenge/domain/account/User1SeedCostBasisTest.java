package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class User1SeedCostBasisTest {

    private static final AccountId ACCOUNT = AccountId.of(1L);
    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("PF-11 · Rendimiento de PAMP en la seed")
    void pf11() {
        Position pamp = seed().position(User1SeedLedgerFixture.PAMP).orElseThrow();

        assertThat(pamp.quantity()).isEqualTo(40L);
        assertThat(pamp.averageCost().orElseThrow()).isEqualByComparingTo("930");
    }

    @Test
    @DisplayName("PF-13 · Datos complementarios de PAMP en la seed")
    void pf13() {
        assertThat(seed().position(User1SeedLedgerFixture.PAMP).orElseThrow().realizedPnl())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("PF-14 · BMA negativo en la seed")
    void pf14() {
        Position bma = seed().position(User1SeedLedgerFixture.BMA).orElseThrow();

        assertThat(bma.quantity()).isEqualTo(-10L);
        assertThat(bma.averageCost()).isEmpty();
    }

    @Test
    @DisplayName("PF-16 · Venta que supera la tenencia")
    void pf16() {
        assertThat(seed().position(User1SeedLedgerFixture.BMA).orElseThrow().realizedPnl())
                .isEqualByComparingTo("-200");
    }

    @Test
    @DisplayName("PF-17 · Compra sobre una tenencia negativa")
    void pf17() {
        Account account = seed();
        account.applyBuyFill(User1SeedLedgerFixture.BMA, 15L, new BigDecimal("1500"));
        Position bma = account.position(User1SeedLedgerFixture.BMA).orElseThrow();

        assertThat(bma.quantity()).isEqualTo(5L);
        assertThat(bma.averageCost().orElseThrow()).isEqualByComparingTo("1500");
        assertThat(bma.realizedPnl()).isEqualByComparingTo("-200");
    }

    private static Account seed() {
        return Account.rebuildFrom(ACCOUNT, User1SeedLedgerFixture.entries().stream(), COST_BASIS);
    }
}
