package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionCostBasisScenariosTest {

    private static final AccountId ACCOUNT = AccountId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);
    private static final Instant T0 = Instant.parse("2023-07-12T13:00:00Z");

    @Test
    @DisplayName("PF-08 · Compras a distintos precios")
    void pf08() {
        Position position = rebuild(filled(1, GGAL, OrderSide.BUY, "10", "100", T0),
                        filled(2, GGAL, OrderSide.BUY, "10", "200", T0.plusSeconds(1)))
                .position(GGAL)
                .orElseThrow();

        assertThat(position.quantity()).isEqualTo(20L);
        assertThat(position.averageCost().orElseThrow()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("PF-09 · Una venta parcial no cambia el costo promedio")
    void pf09() {
        Position position = rebuild(
                        filled(1, GGAL, OrderSide.BUY, "10", "100", T0),
                        filled(2, GGAL, OrderSide.BUY, "10", "200", T0.plusSeconds(1)),
                        filled(3, GGAL, OrderSide.SELL, "10", "150", T0.plusSeconds(2)))
                .position(GGAL)
                .orElseThrow();

        assertThat(position.quantity()).isEqualTo(10L);
        assertThat(position.averageCost().orElseThrow()).isEqualByComparingTo("150");
    }

    @Test
    @DisplayName("PF-10 · Una posición que vuelve a cero reinicia el costo")
    void pf10() {
        Position position = rebuild(
                        filled(1, GGAL, OrderSide.BUY, "10", "100", T0),
                        filled(2, GGAL, OrderSide.SELL, "10", "120", T0.plusSeconds(1)),
                        filled(3, GGAL, OrderSide.BUY, "10", "200", T0.plusSeconds(2)))
                .position(GGAL)
                .orElseThrow();

        assertThat(position.quantity()).isEqualTo(10L);
        assertThat(position.averageCost().orElseThrow()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("PF-11 · Rendimiento de PAMP en la seed")
    void pf11() {
        Position pamp = seed().position(User1SeedLedgerFixture.PAMP).orElseThrow();

        assertThat(pamp.quantity()).isEqualTo(40L);
        assertThat(pamp.averageCost().orElseThrow()).isEqualByComparingTo("930");
        assertThat(pamp.realizedPnl()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("PF-12 · Resultado realizado de una venta parcial")
    void pf12() {
        Position position = rebuild(
                        filled(1, GGAL, OrderSide.BUY, "10", "100", T0),
                        filled(2, GGAL, OrderSide.BUY, "10", "200", T0.plusSeconds(1)),
                        filled(3, GGAL, OrderSide.SELL, "10", "180", T0.plusSeconds(2)))
                .position(GGAL)
                .orElseThrow();

        assertThat(position.realizedPnl()).isEqualByComparingTo("300");
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
        return Account.rebuildFrom(ACCOUNT, User1SeedLedgerFixture.entries().stream());
    }

    private static Account rebuild(LedgerEntry... entries) {
        return Account.rebuildFrom(ACCOUNT, Stream.of(entries));
    }

    private static LedgerEntry filled(
            long id, InstrumentId instrumentId, OrderSide side, String size, String price, Instant at) {
        return new LedgerEntry(
                OrderId.of(id),
                instrumentId,
                side,
                OrderType.MARKET,
                OrderStatus.FILLED,
                new BigDecimal(size),
                new BigDecimal(price),
                at);
    }
}
