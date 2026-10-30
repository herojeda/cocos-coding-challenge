package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.domain.order.OrderSide;
import com.cocoscap.challenge.domain.order.OrderStatus;
import com.cocoscap.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LedgerEntryTest {

    private static final LocalDateTime AT = LocalDateTime.of(2023, 7, 12, 12, 0);

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEntries")
    void rejectsInvalidArguments(String caseName, Runnable factory) {
        assertThatThrownBy(factory::run).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidEntries() {
        return Stream.of(
                Arguments.of("null side", (Runnable) () -> entry(null, OrderType.MARKET, OrderStatus.FILLED, "10", "1")),
                Arguments.of("null type", (Runnable) () -> entry(OrderSide.BUY, null, OrderStatus.FILLED, "10", "1")),
                Arguments.of(
                        "null status", (Runnable) () -> entry(OrderSide.BUY, OrderType.MARKET, null, "10", "1")),
                Arguments.of(
                        "null size",
                        (Runnable) () -> entry(OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED, null, "1")),
                Arguments.of(
                        "null price",
                        (Runnable) () -> entry(OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED, "10", null)),
                Arguments.of(
                        "null datetime",
                        (Runnable) () -> new LedgerEntry(
                                1L, 34L, OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED,
                                new BigDecimal("10"), BigDecimal.ONE, null)),
                Arguments.of(
                        "non-positive size",
                        (Runnable) () -> entry(OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED, "0", "1")),
                Arguments.of(
                        "non-positive price",
                        (Runnable) () -> entry(OrderSide.BUY, OrderType.MARKET, OrderStatus.FILLED, "10", "0")));
    }

    private static LedgerEntry entry(
            OrderSide side, OrderType type, OrderStatus status, String size, String price) {
        return new LedgerEntry(
                1L,
                34L,
                side,
                type,
                status,
                size == null ? null : new BigDecimal(size),
                price == null ? null : new BigDecimal(price),
                AT);
    }
}
