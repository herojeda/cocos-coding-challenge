package com.cocoscap.challenge.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.User1SeedLedgerFixture;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.instrument.InstrumentType;
import com.cocoscap.challenge.domain.market.Quote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioValuationTest {

    private static final AccountId USER1_ACCOUNT = AccountId.of(1L);
    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();
    private static final PortfolioValuation VALUATION = new PortfolioValuation();

    private static final Instrument BMA =
            new Instrument(User1SeedLedgerFixture.BMA, "BMA", "Banco Macro S.A.", InstrumentType.STOCK);
    private static final Instrument PAMP =
            new Instrument(User1SeedLedgerFixture.PAMP, "PAMP", "Pampa Holding S.A.", InstrumentType.STOCK);
    private static final Instrument METR =
            new Instrument(User1SeedLedgerFixture.METR, "METR", "MetroGAS S.A.", InstrumentType.STOCK);

    private static final Map<InstrumentId, Instrument> SEED_INSTRUMENTS = index(BMA, PAMP, METR);

    private static final LocalDate JUL_14 = LocalDate.of(2023, 7, 14);

    private static final Quote PAMP_JUL_14 =
            new Quote(User1SeedLedgerFixture.PAMP, JUL_14, new BigDecimal("925.85"), new BigDecimal("921.80"));
    private static final Quote METR_JUL_14 =
            new Quote(User1SeedLedgerFixture.METR, JUL_14, new BigDecimal("229.50"), new BigDecimal("232.00"));
    private static final Quote BMA_JUL_14 =
            new Quote(User1SeedLedgerFixture.BMA, JUL_14, new BigDecimal("1502.80"), new BigDecimal("1520.25"));

    private static final Map<InstrumentId, Quote> SEED_QUOTES = Map.of(
            User1SeedLedgerFixture.PAMP, PAMP_JUL_14,
            User1SeedLedgerFixture.METR, METR_JUL_14,
            User1SeedLedgerFixture.BMA, BMA_JUL_14);

    @Test
    @DisplayName("PF-01 · Portfolio del usuario de la seed")
    void pf01() {
        Portfolio portfolio = VALUATION.value(seedAccount(), SEED_INSTRUMENTS, SEED_QUOTES);

        assertThat(portfolio.availableCash()).isEqualByComparingTo("627500");
        assertThat(portfolio.positions())
                .extracting(PortfolioPosition::ticker, PortfolioPosition::quantity)
                .containsExactlyInAnyOrder(tuple("PAMP", 40L), tuple("METR", 500L), tuple("BMA", -10L));
        // 753_000 + 37_034 + 114_750 − 15_028
        assertThat(portfolio.totalValue()).isEqualByComparingTo("889756");
    }

    @Test
    @DisplayName("PF-11 · Rendimiento de PAMP en la seed")
    void pf11() {
        PortfolioPosition pamp = position(VALUATION.value(seedAccount(), SEED_INSTRUMENTS, SEED_QUOTES), "PAMP");

        assertThat(pamp.quantity()).isEqualTo(40L);
        assertThat(pamp.averageCost().orElseThrow()).isEqualByComparingTo("930");
        assertThat(pamp.totalReturn().orElseThrow()).isEqualByComparingTo("-0.45");
    }

    @Test
    @DisplayName("PF-12 · Resultado realizado de una venta parcial")
    void pf12() {
        Instrument ggal = new Instrument(InstrumentId.of(34L), "GGAL", "Grupo Financiero Galicia", InstrumentType.STOCK);
        Account account = Account.empty(AccountId.of(99L), COST_BASIS);
        account.cashIn(new BigDecimal("100000"));
        account.applyBuyFill(ggal.id(), 10L, new BigDecimal("100"));
        account.applyBuyFill(ggal.id(), 10L, new BigDecimal("200"));
        account.applySellFill(ggal.id(), 10L, new BigDecimal("180"));

        Portfolio portfolio = VALUATION.value(
                account,
                index(ggal),
                Map.of(
                        ggal.id(),
                        new Quote(ggal.id(), JUL_14, new BigDecimal("180"), new BigDecimal("180"))));

        assertThat(position(portfolio, "GGAL").realizedPnl()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("PF-13 · Datos complementarios de PAMP en la seed")
    void pf13() {
        PortfolioPosition pamp = position(VALUATION.value(seedAccount(), SEED_INSTRUMENTS, SEED_QUOTES), "PAMP");

        assertThat(pamp.realizedPnl()).isEqualByComparingTo("100");
        assertThat(pamp.dailyChange().orElseThrow()).isEqualByComparingTo("0.44");
    }

    @Test
    @DisplayName("PF-14 · BMA negativo en la seed")
    void pf14() {
        PortfolioPosition bma = position(VALUATION.value(seedAccount(), SEED_INSTRUMENTS, SEED_QUOTES), "BMA");

        assertThat(bma.quantity()).isEqualTo(-10L);
        assertThat(bma.marketValue().orElseThrow()).isEqualByComparingTo("-15028");
        assertThat(bma.averageCost()).isEmpty();
        assertThat(bma.totalReturn()).isEmpty();
    }

    @Test
    @DisplayName("PF-16 · Venta que supera la tenencia")
    void pf16() {
        PortfolioPosition bma = position(VALUATION.value(seedAccount(), SEED_INSTRUMENTS, SEED_QUOTES), "BMA");

        assertThat(bma.realizedPnl()).isEqualByComparingTo("-200");
    }

    @Test
    @DisplayName("PF-17 · Compra sobre una tenencia negativa")
    void pf17() {
        Account account = seedAccount();
        account.applyBuyFill(User1SeedLedgerFixture.BMA, 15L, new BigDecimal("1500"));

        PortfolioPosition bma = position(VALUATION.value(account, SEED_INSTRUMENTS, SEED_QUOTES), "BMA");

        assertThat(bma.quantity()).isEqualTo(5L);
        assertThat(bma.averageCost().orElseThrow()).isEqualByComparingTo("1500");
        assertThat(bma.realizedPnl()).isEqualByComparingTo("-200");
    }

    private static Account seedAccount() {
        return Account.rebuildFrom(
                USER1_ACCOUNT, User1SeedLedgerFixture.entries().stream(), COST_BASIS);
    }

    private static PortfolioPosition position(Portfolio portfolio, String ticker) {
        return portfolio.positions().stream()
                .filter(p -> p.ticker().equals(ticker))
                .findFirst()
                .orElseThrow();
    }

    private static Map<InstrumentId, Instrument> index(Instrument... instruments) {
        return Stream.of(instruments).collect(Collectors.toMap(Instrument::id, Function.identity()));
    }
}
