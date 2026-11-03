package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.Order;
import com.cocoscap.challenge.domain.order.OrderDecision;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.OrderPlacementPolicy;
import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import com.cocoscap.challenge.domain.order.NewOrder;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountReleaseTest {

    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();
    private static final AccountId ACCOUNT_ID = AccountId.of(1L);
    private static final UserId USER = UserId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);
    private static final InstrumentId PAMP = InstrumentId.of(47L);
    private static final Instant PLACED_AT = Instant.parse("2023-07-14T15:00:00Z");

    @Test
    @DisplayName("CXL-01 · Cancelación de una compra límite")
    void cxl01CancelBuyLimitReleasesCash() {
        Account account = cashAccount("100000");
        OrderDecision decision = OrderPlacementPolicy.decide(
                new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, null, new BigDecimal("850")),
                account.availableCash(),
                account.availableQuantity(GGAL),
                Optional.empty());
        account.apply(decision);
        assertThat(account.availableCash()).isEqualByComparingTo("91500");

        Order cancelled = decision.toOrder(OrderId.of(1L), PLACED_AT).cancel();
        account.release(cancelled);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(account.availableCash()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("CXL-02 · Cancelación de una venta límite")
    void cxl02CancelSellLimitReleasesShares() {
        Account account = cashAccount("1000000");
        account.applyBuyFill(PAMP, 40L, new BigDecimal("900"));
        OrderDecision decision = OrderPlacementPolicy.decide(
                new NewOrder(USER, PAMP, OrderSide.SELL, OrderType.LIMIT, 15L, null, new BigDecimal("950")),
                account.availableCash(),
                account.availableQuantity(PAMP),
                Optional.empty());
        account.apply(decision);
        assertThat(account.availableQuantity(PAMP)).isEqualTo(25L);

        Order cancelled = decision.toOrder(OrderId.of(2L), PLACED_AT).cancel();
        account.release(cancelled);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(account.availableQuantity(PAMP)).isEqualTo(40L);
    }

    @Test
    @DisplayName("Cancelling releases the reservation of an order placed by amount")
    void cancelAmountBasedBuyLimitReleasesSizeTimesPrice() {
        Account account = cashAccount("100000");
        OrderDecision decision = OrderPlacementPolicy.decide(
                new NewOrder(USER, GGAL, OrderSide.BUY, OrderType.LIMIT, null, new BigDecimal("10000"), new BigDecimal("900")),
                account.availableCash(),
                account.availableQuantity(GGAL),
                Optional.empty());
        account.apply(decision);
        // 10000 / 900 = 11 shares truncated; reservation is 11 × 900 = 9900
        assertThat(decision.size()).isEqualTo(11L);
        assertThat(account.availableCash()).isEqualByComparingTo("90100");

        Order cancelled = decision.toOrder(OrderId.of(3L), PLACED_AT).cancel();
        account.release(cancelled);

        assertThat(account.availableCash()).isEqualByComparingTo("100000");
        assertThat(account.reservedCash()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("release() rejects an order that is not CANCELLED")
    void releaseRequiresCancelledStatus() {
        Account account = cashAccount("100000");
        Order order = Order.accepted(
                OrderId.of(4L),
                USER,
                GGAL,
                OrderSide.BUY,
                OrderType.LIMIT,
                10L,
                new BigDecimal("850"),
                null,
                PLACED_AT);

        assertThatThrownBy(() -> account.release(order)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Account cashAccount(String cash) {
        Account account = Account.empty(ACCOUNT_ID, COST_BASIS);
        account.cashIn(new BigDecimal(cash));
        return account;
    }
}
