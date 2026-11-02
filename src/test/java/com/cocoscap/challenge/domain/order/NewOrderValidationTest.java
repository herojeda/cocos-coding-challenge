package com.cocoscap.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewOrderValidationTest {

    private static final UserId USER = UserId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);

    @Test
    @DisplayName("ORD-01 · Orden LIMIT sin precio")
    void ord01LimitWithoutPrice() {
        assertThatThrownBy(() -> newOrder(OrderSide.BUY, OrderType.LIMIT, 10L, null, null))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("price");
    }

    @Test
    @DisplayName("ORD-02 · Orden MARKET con precio")
    void ord02MarketWithPrice() {
        assertThatThrownBy(
                        () -> newOrder(OrderSide.BUY, OrderType.MARKET, 10L, null, new BigDecimal("900")))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("price");
    }

    @Test
    @DisplayName("ORD-03 · Cantidad y monto a la vez")
    void ord03SizeAndAmountTogether() {
        assertThatThrownBy(
                        () -> newOrder(
                                OrderSide.BUY,
                                OrderType.MARKET,
                                10L,
                                new BigDecimal("10000"),
                                null))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("amount");
    }

    @Test
    @DisplayName("ORD-04 · Cantidad no positiva")
    void ord04NonPositiveSize() {
        assertThatThrownBy(() -> newOrder(OrderSide.BUY, OrderType.MARKET, 0L, null, null))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("size");
    }

    @Test
    @DisplayName("ORD-05 · Lado de transferencia")
    void ord05CashOutSide() {
        assertThatThrownBy(() -> newOrder(OrderSide.CASH_OUT, OrderType.MARKET, 10L, null, null))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("side");
    }

    @Test
    @DisplayName("LIMIT price with more than 2 decimal places is rejected")
    void rejectsPriceWithExtraScale() {
        assertThatThrownBy(
                        () -> newOrder(
                                OrderSide.BUY, OrderType.LIMIT, 10L, null, new BigDecimal("850.005")))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("price");
    }

    @Test
    @DisplayName("LIMIT price 850 and 850.00 normalize to the same value")
    void normalizesPriceScale() {
        NewOrder asInteger = newOrder(OrderSide.BUY, OrderType.LIMIT, 10L, null, new BigDecimal("850"));
        NewOrder asScaled =
                newOrder(OrderSide.BUY, OrderType.LIMIT, 10L, null, new BigDecimal("850.00"));
        assertThat(asInteger.price()).isEqualByComparingTo(asScaled.price());
        assertThat(asInteger.price().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("LIMIT price beyond NUMERIC(10,2) is rejected")
    void rejectsPriceOutOfRange() {
        assertThatThrownBy(
                        () -> newOrder(
                                OrderSide.BUY,
                                OrderType.LIMIT,
                                10L,
                                null,
                                new BigDecimal("100000000.00")))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("price");
    }

    @Test
    @DisplayName("Amount beyond NUMERIC(18,2) is rejected")
    void rejectsAmountOutOfRange() {
        assertThatThrownBy(
                        () -> newOrder(
                                OrderSide.BUY,
                                OrderType.MARKET,
                                null,
                                new BigDecimal("100000000000000000.00"),
                                null))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("amount");
    }

    @Test
    @DisplayName("Size beyond NUMERIC(18,2) integer digits is rejected")
    void rejectsSizeOutOfRange() {
        assertThatThrownBy(
                        () -> newOrder(
                                OrderSide.SELL, OrderType.MARKET, NewOrder.MAX_SIZE + 1L, null, null))
                .isInstanceOf(NewOrderValidationException.class)
                .extracting(ex -> ((NewOrderValidationException) ex).field())
                .isEqualTo("size");
    }

    private static NewOrder newOrder(
            OrderSide side, OrderType type, Long size, BigDecimal amount, BigDecimal price) {
        return new NewOrder(USER, GGAL, side, type, size, amount, price);
    }
}
