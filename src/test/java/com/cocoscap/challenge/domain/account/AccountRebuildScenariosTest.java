package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
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

class AccountRebuildScenariosTest {

    private static final AccountId ACCOUNT = AccountId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);
    private static final InstrumentId PAMP = InstrumentId.of(47L);
    private static final InstrumentId ARS = InstrumentId.of(66L);
    private static final Instant T0 = Instant.parse("2023-07-12T13:00:00Z");
    private static final Instant T1 = T0.plusSeconds(3600);
    private static final Instant T2 = T0.plusSeconds(7200);
    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("ACB-01 · Órdenes no ejecutadas no mueven el efectivo")
    void acb01() {
        Account account = rebuild(
                cashIn(1, "100000", T0),
                order(2, GGAL, OrderSide.BUY, OrderType.MARKET, OrderStatus.REJECTED, "10", "1000", T1),
                order(3, GGAL, OrderSide.BUY, OrderType.LIMIT, OrderStatus.CANCELLED, "10", "900", T2));
        assertThat(account.cash()).isEqualByComparingTo("100000");
        assertThat(account.position(GGAL)).isEmpty();
    }

    @Test
    @DisplayName("ACB-02 · Compras y ventas ejecutadas")
    void acb02() {
        Account account = rebuild(
                cashIn(1, "100000", T0),
                filled(2, GGAL, OrderSide.BUY, "10", "1000", T1),
                filled(3, GGAL, OrderSide.SELL, "5", "1200", T2));
        assertThat(account.cash()).isEqualByComparingTo("96000");
    }

    @Test
    @DisplayName("ACB-03 · Transferencia saliente")
    void acb03() {
        assertThat(rebuild(cashIn(1, "100000", T0), cashOut(2, "30000", T1)).cash())
                .isEqualByComparingTo("70000");
    }

    @Test
    @DisplayName("ACB-04 · Varias compras y una venta")
    void acb04() {
        Account account = rebuild(
                filled(1, PAMP, OrderSide.BUY, "50", "900", T0),
                filled(2, PAMP, OrderSide.BUY, "20", "910", T1),
                filled(3, PAMP, OrderSide.SELL, "10", "920", T2));
        assertThat(account.position(PAMP).orElseThrow().quantity()).isEqualTo(60L);
    }

    @Test
    @DisplayName("ACB-05 · Posición cerrada")
    void acb05() {
        Account account = rebuild(
                filled(1, GGAL, OrderSide.BUY, "10", "1000", T0),
                filled(2, GGAL, OrderSide.SELL, "10", "1100", T1));
        assertThat(account.position(GGAL).orElseThrow().quantity()).isZero();
    }

    @Test
    @DisplayName("ACB-07 · Reserva de pesos por una compra límite")
    void acb07() {
        Account account = rebuild(
                cashIn(1, "100000", T0),
                order(2, GGAL, OrderSide.BUY, OrderType.LIMIT, OrderStatus.NEW, "10", "900", T1));
        assertThat(account.reservedCash()).isEqualByComparingTo("9000");
        assertThat(account.cash()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("ACB-08 · Reserva de acciones por una venta límite")
    void acb08() {
        Account account = rebuild(
                filled(1, PAMP, OrderSide.BUY, "40", "900", T0),
                order(2, PAMP, OrderSide.SELL, OrderType.LIMIT, OrderStatus.NEW, "15", "950", T1));
        assertThat(account.position(PAMP).orElseThrow().reservedQuantity()).isEqualTo(15L);
        assertThat(account.position(PAMP).orElseThrow().quantity()).isEqualTo(40L);
    }

    @Test
    @DisplayName("ACB-09 · Una orden cancelada no reserva")
    void acb09() {
        Account account = rebuild(
                cashIn(1, "100000", T0),
                order(2, GGAL, OrderSide.BUY, OrderType.LIMIT, OrderStatus.CANCELLED, "10", "900", T1));
        assertThat(account.reservedCash()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ACB-10 · Disponible con una compra límite pendiente")
    void acb10() {
        Account account = rebuild(
                cashIn(1, "100000", T0),
                order(2, GGAL, OrderSide.BUY, OrderType.LIMIT, OrderStatus.NEW, "10", "900", T1));
        assertThat(account.availableCash()).isEqualByComparingTo("91000");
    }

    @Test
    @DisplayName("ACB-12 · Disponible con una venta límite pendiente")
    void acb12() {
        Account account = rebuild(
                filled(1, PAMP, OrderSide.BUY, "40", "900", T0),
                order(2, PAMP, OrderSide.SELL, OrderType.LIMIT, OrderStatus.NEW, "15", "950", T1));
        assertThat(account.availableQuantity(PAMP)).isEqualTo(25L);
    }

    private static Account rebuild(LedgerEntry... entries) {
        return Account.rebuildFrom(ACCOUNT, Stream.of(entries), COST_BASIS);
    }

    private static LedgerEntry cashIn(long id, String amount, Instant at) {
        return order(id, ARS, OrderSide.CASH_IN, OrderType.MARKET, OrderStatus.FILLED, amount, "1", at);
    }

    private static LedgerEntry cashOut(long id, String amount, Instant at) {
        return order(id, ARS, OrderSide.CASH_OUT, OrderType.MARKET, OrderStatus.FILLED, amount, "1", at);
    }

    private static LedgerEntry filled(
            long id, InstrumentId instrumentId, OrderSide side, String size, String price, Instant at) {
        return order(id, instrumentId, side, OrderType.MARKET, OrderStatus.FILLED, size, price, at);
    }

    private static LedgerEntry order(
            long id,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            OrderStatus status,
            String size,
            String price,
            Instant at) {
        return new LedgerEntry(
                OrderId.of(id),
                instrumentId,
                side,
                type,
                status,
                new BigDecimal(size),
                new BigDecimal(price),
                at);
    }
}
