package com.cocoscap.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderDecisionTest {

    private static final UserId USER = UserId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);
    private static final Instant PLACED_AT = Instant.parse("2023-07-14T15:00:00Z");
    private static final BigDecimal PRICE = new BigDecimal("850.00");
    private static final BigDecimal AMOUNT = new BigDecimal("10000.00");

    @Test
    @DisplayName("OrderDecision becomes a registered Order only after insert assigns an id")
    void toOrderAttachesPersistedId() {
        OrderDecision decision = OrderDecision.accepted(
                USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, PRICE, null);

        Order order = decision.toOrder(OrderId.of(42L), PLACED_AT);

        assertThat(order.id()).isEqualTo(OrderId.of(42L));
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.size()).isEqualTo(10L);
        assertThat(order.price()).isEqualByComparingTo(PRICE);
        assertThat(order.datetime()).isEqualTo(PLACED_AT);
    }

    @Test
    @DisplayName("Null size is accepted only for NO_MARKET_PRICE rejection with amount")
    void nullSizeAcceptedForUnpricedAmountRejection() {
        OrderDecision decision = OrderDecision.rejected(
                USER,
                GGAL,
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                null,
                AMOUNT,
                RejectionReason.NO_MARKET_PRICE);

        assertThat(decision.size()).isNull();
        assertThat(decision.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(decision.rejectionReason()).isEqualTo(RejectionReason.NO_MARKET_PRICE);
    }

    @Test
    @DisplayName("Null size is rejected for FILLED")
    void nullSizeRejectedForFilled() {
        assertThatThrownBy(() -> new OrderDecision(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        PRICE,
                        null,
                        OrderStatus.FILLED,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("Null size is rejected for NO_MARKET_PRICE without amount")
    void nullSizeRejectedWithoutAmount() {
        assertThatThrownBy(() -> OrderDecision.rejected(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        null,
                        null,
                        RejectionReason.NO_MARKET_PRICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("Rejection reason is required for REJECTED and forbidden otherwise")
    void rejectionReasonOnlyWhenRejected() {
        OrderDecision rejected = OrderDecision.rejected(
                USER,
                GGAL,
                OrderSide.BUY,
                OrderType.MARKET,
                10L,
                PRICE,
                null,
                RejectionReason.INSUFFICIENT_CASH);
        assertThat(rejected.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_CASH);

        assertThatThrownBy(() -> new OrderDecision(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.MARKET,
                        10L,
                        PRICE,
                        null,
                        OrderStatus.REJECTED,
                        null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new OrderDecision(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.MARKET,
                        10L,
                        PRICE,
                        null,
                        OrderStatus.FILLED,
                        RejectionReason.INSUFFICIENT_CASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionReason");

        assertThatThrownBy(() -> new OrderDecision(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        10L,
                        PRICE,
                        null,
                        OrderStatus.NEW,
                        RejectionReason.INSUFFICIENT_CASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionReason");
    }

    @Test
    @DisplayName("accepted accepts LIMIT and rejects MARKET")
    void acceptedRequiresLimit() {
        OrderDecision limit = OrderDecision.accepted(
                USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, PRICE, null);
        assertThat(limit.status()).isEqualTo(OrderStatus.NEW);
        assertThat(limit.type()).isEqualTo(OrderType.LIMIT);

        assertThatThrownBy(() -> OrderDecision.accepted(
                        USER, GGAL, OrderSide.BUY, OrderType.MARKET, 10L, PRICE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIMIT");
    }

    @Test
    @DisplayName("Side must be BUY or SELL")
    void sideMustBeBuyOrSell() {
        OrderDecision buy = OrderDecision.filled(
                USER, GGAL, OrderSide.BUY, OrderType.MARKET, 10L, PRICE, null);
        assertThat(buy.side()).isEqualTo(OrderSide.BUY);

        assertThatThrownBy(() -> new OrderDecision(
                        USER,
                        GGAL,
                        OrderSide.CASH_IN,
                        OrderType.MARKET,
                        10L,
                        PRICE,
                        null,
                        OrderStatus.FILLED,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("side");
    }

    @Test
    @DisplayName("Size, price and amount must be positive when present")
    void quantityPriceAndAmountMustBePositive() {
        OrderDecision ok = OrderDecision.filled(
                USER, GGAL, OrderSide.SELL, OrderType.MARKET, 5L, PRICE, AMOUNT);
        assertThat(ok.size()).isEqualTo(5L);
        assertThat(ok.price()).isEqualByComparingTo(PRICE);
        assertThat(ok.amount()).isEqualByComparingTo(AMOUNT);

        assertThatThrownBy(() -> OrderDecision.filled(
                        USER, GGAL, OrderSide.BUY, OrderType.MARKET, 0L, PRICE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");

        assertThatThrownBy(() -> OrderDecision.filled(
                        USER, GGAL, OrderSide.BUY, OrderType.MARKET, 10L, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");

        assertThatThrownBy(() -> OrderDecision.filled(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.MARKET,
                        10L,
                        PRICE,
                        BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("NO_MARKET_PRICE requires MARKET without price")
    void noMarketPriceRequiresMarketWithoutPrice() {
        OrderDecision ok = OrderDecision.rejected(
                USER,
                GGAL,
                OrderSide.BUY,
                OrderType.MARKET,
                10L,
                null,
                null,
                RejectionReason.NO_MARKET_PRICE);
        assertThat(ok.price()).isNull();
        assertThat(ok.type()).isEqualTo(OrderType.MARKET);

        assertThatThrownBy(() -> OrderDecision.rejected(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        10L,
                        null,
                        null,
                        RejectionReason.NO_MARKET_PRICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_MARKET_PRICE");

        assertThatThrownBy(() -> OrderDecision.rejected(
                        USER,
                        GGAL,
                        OrderSide.BUY,
                        OrderType.MARKET,
                        10L,
                        PRICE,
                        null,
                        RejectionReason.NO_MARKET_PRICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_MARKET_PRICE");
    }
}
