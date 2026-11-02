package com.cocoscap.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.User1SeedLedgerFixture;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.market.Quote;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderPlacementPolicyTest {

    private static final AccountId ACCOUNT_ID = AccountId.of(1L);
    private static final UserId USER = UserId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);
    private static final InstrumentId PAMP = InstrumentId.of(47L);
    private static final InstrumentId PGR = InstrumentId.of(3L);
    private static final InstrumentId BMA = User1SeedLedgerFixture.BMA;
    private static final LocalDate QUOTE_DATE = LocalDate.of(2023, 7, 14);
    private static final Instant PLACED_AT = Instant.parse("2023-07-14T15:00:00Z");
    private static final OrderId ORDER_ID = OrderId.of(100L);
    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();
    private static final BigDecimal GGAL_CLOSE = new BigDecimal("885.80");
    private static final BigDecimal PAMP_CLOSE = new BigDecimal("925.85");

    @Test
    @DisplayName("ORD-08 · Compra MARKET por monto")
    void ord08() {
        Account account = cashAccount("100000");
        OrderDecision decision = decide(
                buyMarketAmount(GGAL, "10000"), account, Optional.of(quote(GGAL, GGAL_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.size()).isEqualTo(11L);
        assertThat(order.price()).isEqualByComparingTo(GGAL_CLOSE);
        account.apply(decision);
        assertThat(account.availableCash()).isEqualByComparingTo("90256.20");
    }

    @Test
    @DisplayName("ORD-09 · Compra LIMIT por monto")
    void ord09() {
        Account account = cashAccount("100000");
        OrderDecision decision =
                decide(buyLimitAmount(GGAL, "900", "10000"), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.size()).isEqualTo(11L);
        assertThat(order.price()).isEqualByComparingTo("900");
        account.apply(decision);
        assertThat(account.availableCash()).isEqualByComparingTo("90100");
    }

    @Test
    @DisplayName("ORD-10 · Venta MARKET por monto")
    void ord10() {
        Account account = pampAccount(40);
        OrderDecision decision = decide(
                sellMarketAmount(PAMP, "5000"), account, Optional.of(quote(PAMP, PAMP_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.size()).isEqualTo(5L);
        assertThat(order.price()).isEqualByComparingTo(PAMP_CLOSE);
    }

    @Test
    @DisplayName("ORD-11 · Monto que no alcanza para una acción")
    void ord11() {
        Account account = cashAccount("100000");
        assertThatThrownBy(() -> decide(
                        buyMarketAmount(GGAL, "500"), account, Optional.of(quote(GGAL, GGAL_CLOSE))))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("amount");
    }

    @Test
    @DisplayName("Amount that yields more than MAX_SIZE shares is a validation error")
    void amountYieldingOversizedSizeIsValidationError() {
        Account account = cashAccount("1");
        // max amount / 0.01 = 10^18 shares > MAX_SIZE (16 digits)
        NewOrder request = new NewOrder(
                USER,
                GGAL,
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                new BigDecimal("9999999999999999.99"),
                null);
        assertThatThrownBy(() -> decide(request, account, Optional.of(quote(GGAL, "0.01"))))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("amount");
    }

    @Test
    @DisplayName("ORD-12 · Compra MARKET")
    void ord12() {
        Account account = cashAccount("100000");
        OrderDecision decision =
                decide(buyMarketSize(GGAL, 10L), account, Optional.of(quote(GGAL, GGAL_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.size()).isEqualTo(10L);
        assertThat(order.price()).isEqualByComparingTo(GGAL_CLOSE);
        account.apply(decision);
        assertThat(account.cash()).isEqualByComparingTo("91142");
        assertThat(account.position(GGAL).orElseThrow().quantity()).isEqualTo(10L);
    }

    @Test
    @DisplayName("ORD-13 · Venta MARKET")
    void ord13() {
        Account account = pampAccount(40);
        BigDecimal cashBefore = account.cash();
        OrderDecision decision =
                decide(sellMarketSize(PAMP, 10L), account, Optional.of(quote(PAMP, PAMP_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.size()).isEqualTo(10L);
        assertThat(order.price()).isEqualByComparingTo(PAMP_CLOSE);
        account.apply(decision);
        assertThat(account.cash()).isEqualByComparingTo(cashBefore.add(new BigDecimal("9258.50")));
        assertThat(account.position(PAMP).orElseThrow().quantity()).isEqualTo(30L);
    }

    @Test
    @DisplayName("ORD-14 · Compra LIMIT")
    void ord14() {
        Account account = cashAccount("100000");
        OrderDecision decision =
                decide(buyLimitSize(GGAL, 10L, "850"), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.size()).isEqualTo(10L);
        assertThat(order.price()).isEqualByComparingTo("850");
        account.apply(decision);
        assertThat(account.availableCash()).isEqualByComparingTo("91500");
        assertThat(account.cash()).isEqualByComparingTo("100000");
        assertThat(account.position(GGAL)).isEmpty();
    }

    @Test
    @DisplayName("ORD-15 · Compra LIMIT por encima del último precio")
    void ord15() {
        Account account = cashAccount("100000");
        OrderDecision decision = decide(
                buyLimitSize(GGAL, 10L, "900"), account, Optional.of(quote(GGAL, GGAL_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        account.apply(decision);
        assertThat(account.cash()).isEqualByComparingTo("100000");
        assertThat(account.position(GGAL)).isEmpty();
    }

    @Test
    @DisplayName("ORD-16 · Venta LIMIT")
    void ord16() {
        Account account = pampAccount(40);
        OrderDecision decision =
                decide(sellLimitSize(PAMP, 15L, "1000"), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.size()).isEqualTo(15L);
        assertThat(order.price()).isEqualByComparingTo("1000");
        account.apply(decision);
        assertThat(account.availableQuantity(PAMP)).isEqualTo(25L);
        assertThat(account.position(PAMP).orElseThrow().quantity()).isEqualTo(40L);
    }

    @Test
    @DisplayName("ORD-17 · Compra MARKET sin pesos suficientes")
    void ord17() {
        Account account = cashAccount("100000");
        OrderDecision decision =
                decide(buyMarketSize(GGAL, 200L), account, Optional.of(quote(GGAL, GGAL_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_CASH);
        account.apply(decision);
        assertThat(account.availableCash()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("ORD-18 · Una reserva previa reduce el disponible")
    void ord18() {
        Account account = cashAccount("100000");
        account.reserveCash(new BigDecimal("90000"));
        OrderDecision decision =
                decide(buyMarketSize(GGAL, 20L), account, Optional.of(quote(GGAL, GGAL_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_CASH);
    }

    @Test
    @DisplayName("ORD-19 · Compra por exactamente el disponible")
    void ord19() {
        Account account = cashAccount("8858");
        OrderDecision decision =
                decide(buyMarketSize(GGAL, 10L), account, Optional.of(quote(GGAL, GGAL_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        account.apply(decision);
        assertThat(account.availableCash()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ORD-20 · Compra LIMIT sin pesos suficientes")
    void ord20() {
        Account account = cashAccount("5000");
        OrderDecision decision =
                decide(buyLimitSize(GGAL, 10L, "850"), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_CASH);
        account.apply(decision);
        assertThat(account.reservedCash()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ORD-21 · Venta de más acciones que la tenencia")
    void ord21() {
        Account account = pampAccount(40);
        OrderDecision decision =
                decide(sellMarketSize(PAMP, 50L), account, Optional.of(quote(PAMP, PAMP_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_HOLDINGS);
        account.apply(decision);
        assertThat(account.position(PAMP).orElseThrow().quantity()).isEqualTo(40L);
    }

    @Test
    @DisplayName("ORD-22 · Acciones comprometidas en una venta límite")
    void ord22() {
        Account account = pampAccount(40);
        account.reserveShares(PAMP, 30L);
        OrderDecision decision =
                decide(sellMarketSize(PAMP, 20L), account, Optional.of(quote(PAMP, PAMP_CLOSE)));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_HOLDINGS);
    }

    @Test
    @DisplayName("ORD-23 · Venta sobre una tenencia negativa")
    void ord23() {
        Account account = Account.rebuildFrom(
                ACCOUNT_ID, User1SeedLedgerFixture.entries().stream(), COST_BASIS);
        OrderDecision decision =
                decide(sellMarketSize(BMA, 1L), account, Optional.of(quote(BMA, "1530")));
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_HOLDINGS);
    }

    @Test
    @DisplayName("ORD-24 · Venta de un instrumento que no tiene")
    void ord24() {
        Account account = cashAccount("100000");
        OrderDecision decision =
                decide(sellLimitSize(GGAL, 5L, "900"), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_HOLDINGS);
    }

    @Test
    @DisplayName("ORD-25 · Compra MARKET sin cotización")
    void ord25() {
        Account account = cashAccount("100000");
        OrderDecision decision = decide(buyMarketSize(PGR, 10L), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.price()).isNull();
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.NO_MARKET_PRICE);
    }

    @Test
    @DisplayName("ORD-26 · Compra LIMIT sin cotización")
    void ord26() {
        Account account = cashAccount("100000");
        OrderDecision decision =
                decide(buyLimitSize(PGR, 10L, "50"), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.size()).isEqualTo(10L);
        assertThat(order.price()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("ORD-30 · Una orden aceptada no tiene motivo de rechazo")
    void ord30() {
        Account account = cashAccount("100000");
        Order order = orderOf(decide(buyLimitSize(GGAL, 10L, "850"), account, Optional.empty()));

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.rejectionReason()).isNull();
    }

    @Test
    @DisplayName("ORD-31 · Sin cotización y sin pesos suficientes")
    void ord31() {
        Account account = Account.empty(ACCOUNT_ID, COST_BASIS);
        Order order = orderOf(decide(buyMarketSize(PGR, 10L), account, Optional.empty()));

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.NO_MARKET_PRICE);
    }

    @Test
    @DisplayName("ORD-41 · Compra MARKET por monto sin cotización")
    void ord41() {
        Account account = cashAccount("100000");
        OrderDecision decision =
                decide(buyMarketAmount(PGR, "10000"), account, Optional.empty());
        Order order = orderOf(decision);

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.size()).isNull();
        assertThat(order.price()).isNull();
        assertThat(order.amount()).isEqualByComparingTo("10000");
        assertThat(order.rejectionReason()).isEqualTo(RejectionReason.NO_MARKET_PRICE);
        account.apply(decision);
        assertThat(account.availableCash()).isEqualByComparingTo("100000");
    }

    private static Order orderOf(OrderDecision decision) {
        return decision.toOrder(ORDER_ID, PLACED_AT);
    }

    private static OrderDecision decide(
            NewOrder newOrder, Account account, Optional<Quote> quote) {
        return OrderPlacementPolicy.decide(
                newOrder,
                account.availableCash(),
                account.availableQuantity(newOrder.instrumentId()),
                quote);
    }

    private static Account cashAccount(String cash) {
        Account account = Account.empty(ACCOUNT_ID, COST_BASIS);
        account.cashIn(new BigDecimal(cash));
        return account;
    }

    private static Account pampAccount(long quantity) {
        Account account = cashAccount("1000000");
        account.applyBuyFill(PAMP, quantity, new BigDecimal("900"));
        return account;
    }

    private static Quote quote(InstrumentId instrumentId, BigDecimal close) {
        return new Quote(instrumentId, QUOTE_DATE, close, close);
    }

    private static Quote quote(InstrumentId instrumentId, String close) {
        return quote(instrumentId, new BigDecimal(close));
    }

    private static NewOrder buyMarketSize(InstrumentId instrumentId, long size) {
        return new NewOrder(USER, instrumentId, OrderSide.BUY, OrderType.MARKET, size, null, null);
    }

    private static NewOrder sellMarketSize(InstrumentId instrumentId, long size) {
        return new NewOrder(USER, instrumentId, OrderSide.SELL, OrderType.MARKET, size, null, null);
    }

    private static NewOrder buyMarketAmount(InstrumentId instrumentId, String amount) {
        return new NewOrder(
                USER, instrumentId, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal(amount), null);
    }

    private static NewOrder sellMarketAmount(InstrumentId instrumentId, String amount) {
        return new NewOrder(
                USER, instrumentId, OrderSide.SELL, OrderType.MARKET, null, new BigDecimal(amount), null);
    }

    private static NewOrder buyLimitSize(InstrumentId instrumentId, long size, String price) {
        return new NewOrder(
                USER, instrumentId, OrderSide.BUY, OrderType.LIMIT, size, null, new BigDecimal(price));
    }

    private static NewOrder sellLimitSize(InstrumentId instrumentId, long size, String price) {
        return new NewOrder(
                USER, instrumentId, OrderSide.SELL, OrderType.LIMIT, size, null, new BigDecimal(price));
    }

    private static NewOrder buyLimitAmount(InstrumentId instrumentId, String price, String amount) {
        return new NewOrder(
                USER,
                instrumentId,
                OrderSide.BUY,
                OrderType.LIMIT,
                null,
                new BigDecimal(amount),
                new BigDecimal(price));
    }
}
