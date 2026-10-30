package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AccountTransitionValidationTest {

    private static final long GGAL = 34L;

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTransitions")
    void rejectsInvalidTransitionArguments(String caseName, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidTransitions() {
        Account account = Account.empty();
        account.cashIn(new BigDecimal("100000"));
        account.applyBuyFill(GGAL, 10, new BigDecimal("1000"));
        account.reserveCash(new BigDecimal("5000"));
        account.reserveShares(GGAL, 3);

        return Stream.of(
                Arguments.of("buy fill non-positive qty", (Runnable) () -> account.applyBuyFill(GGAL, 0, new BigDecimal("1"))),
                Arguments.of(
                        "buy fill non-positive price",
                        (Runnable) () -> account.applyBuyFill(GGAL, 1, BigDecimal.ZERO)),
                Arguments.of(
                        "sell fill non-positive qty",
                        (Runnable) () -> account.applySellFill(GGAL, 0, new BigDecimal("1"))),
                Arguments.of(
                        "sell fill non-positive price",
                        (Runnable) () -> account.applySellFill(GGAL, 1, BigDecimal.ZERO)),
                Arguments.of("reserve cash non-positive", (Runnable) () -> account.reserveCash(BigDecimal.ZERO)),
                Arguments.of("release cash non-positive", (Runnable) () -> account.releaseCash(BigDecimal.ZERO)),
                Arguments.of("reserve shares non-positive", (Runnable) () -> account.reserveShares(GGAL, 0)),
                Arguments.of("release shares non-positive", (Runnable) () -> account.releaseShares(GGAL, 0)),
                Arguments.of("cash in non-positive", (Runnable) () -> account.cashIn(BigDecimal.ZERO)),
                Arguments.of("cash out non-positive", (Runnable) () -> account.cashOut(BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("Release cash fails when reserved cash would go negative")
    void releaseCashRejectsNegativeReserved() {
        Account account = Account.empty();
        account.cashIn(new BigDecimal("1000"));
        account.reserveCash(new BigDecimal("100"));

        assertThatThrownBy(() -> account.releaseCash(new BigDecimal("101")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.reservedCash()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Release shares fails when reserved quantity would go negative")
    void releaseSharesRejectsNegativeReserved() {
        Account account = Account.empty();
        account.applyBuyFill(GGAL, 10, new BigDecimal("100"));
        account.reserveShares(GGAL, 2);

        assertThatThrownBy(() -> account.releaseShares(GGAL, 3)).isInstanceOf(IllegalStateException.class);
        assertThat(account.position(GGAL).orElseThrow().reservedQuantity()).isEqualTo(2L);
    }
}
