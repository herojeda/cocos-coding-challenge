package com.cocoscap.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCancelTest {

    private static final UserId USER = UserId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);
    private static final Instant PLACED_AT = Instant.parse("2023-07-14T15:00:00Z");
    private static final BigDecimal PRICE = new BigDecimal("850.00");

    @Test
    @DisplayName("cancel() on FILLED throws OrderNotCancellableException with that status")
    void cancelFilledThrowsWithCurrentStatus() {
        Order order = Order.filled(
                OrderId.of(1L), USER, GGAL, OrderSide.BUY, OrderType.MARKET, 10L, PRICE, null, PLACED_AT);

        assertThatThrownBy(order::cancel)
                .isInstanceOf(OrderNotCancellableException.class)
                .extracting(ex -> ((OrderNotCancellableException) ex).currentStatus())
                .isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("cancel() on REJECTED throws OrderNotCancellableException with that status")
    void cancelRejectedThrowsWithCurrentStatus() {
        Order order = Order.rejected(
                OrderId.of(2L),
                USER,
                GGAL,
                OrderSide.SELL,
                OrderType.MARKET,
                50L,
                null,
                null,
                RejectionReason.INSUFFICIENT_HOLDINGS,
                PLACED_AT);

        assertThatThrownBy(order::cancel)
                .isInstanceOf(OrderNotCancellableException.class)
                .extracting(ex -> ((OrderNotCancellableException) ex).currentStatus())
                .isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    @DisplayName("cancel() on CANCELLED throws OrderNotCancellableException with that status")
    void cancelAlreadyCancelledThrowsWithCurrentStatus() {
        Order order = Order.accepted(
                        OrderId.of(3L), USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, PRICE, null, PLACED_AT)
                .cancel();

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(order::cancel)
                .isInstanceOf(OrderNotCancellableException.class)
                .extracting(ex -> ((OrderNotCancellableException) ex).currentStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel() message does not include the order id")
    void cancelExceptionMessageOmitsOrderId() {
        Order order = Order.filled(
                OrderId.of(99L), USER, GGAL, OrderSide.BUY, OrderType.MARKET, 10L, PRICE, null, PLACED_AT);

        assertThatThrownBy(order::cancel)
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessageNotContaining("99");
    }
}
