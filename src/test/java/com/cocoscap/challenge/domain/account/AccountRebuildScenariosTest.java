package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountRebuildScenariosTest {

    private static final long GGAL = 34L;
    private static final long PAMP = 47L;
    private static final long ARS = 66L;
    private static final LocalDateTime T0 = LocalDateTime.of(2023, 7, 12, 10, 0);
    private static final LocalDateTime T1 = T0.plusHours(1);
    private static final LocalDateTime T2 = T0.plusHours(2);

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
        return Account.rebuildFrom(List.of(entries));
    }

    private static LedgerEntry cashIn(long id, String amount, LocalDateTime at) {
        return order(id, ARS, OrderSide.CASH_IN, OrderType.MARKET, OrderStatus.FILLED, amount, "1", at);
    }

    private static LedgerEntry cashOut(long id, String amount, LocalDateTime at) {
        return order(id, ARS, OrderSide.CASH_OUT, OrderType.MARKET, OrderStatus.FILLED, amount, "1", at);
    }

    private static LedgerEntry filled(
            long id, long instrumentId, OrderSide side, String size, String price, LocalDateTime at) {
        return order(id, instrumentId, side, OrderType.MARKET, OrderStatus.FILLED, size, price, at);
    }

    private static LedgerEntry order(
            long id,
            long instrumentId,
            OrderSide side,
            OrderType type,
            OrderStatus status,
            String size,
            String price,
            LocalDateTime at) {
        return new LedgerEntry(
                id, instrumentId, side, type, status, new BigDecimal(size), new BigDecimal(price), at);
    }
}
