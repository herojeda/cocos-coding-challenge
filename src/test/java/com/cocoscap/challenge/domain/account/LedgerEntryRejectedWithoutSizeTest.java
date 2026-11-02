package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class LedgerEntryRejectedWithoutSizeTest {

    private static final AccountId ACCOUNT = AccountId.of(1L);
    private static final InstrumentId PGR = InstrumentId.of(3L);
    private static final Instant T0 = Instant.parse("2023-07-12T13:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @Test
    @DisplayName("REJECTED without size does not alter the account")
    void rejectedWithoutSizeLeavesAccountUnchanged() {
        Account account = Account.rebuildFrom(
                ACCOUNT,
                Stream.of(
                        cashIn(),
                        new LedgerEntry(
                                OrderId.of(2L),
                                PGR,
                                OrderSide.BUY,
                                OrderType.MARKET,
                                OrderStatus.REJECTED,
                                null,
                                null,
                                T1)),
                new WeightedAverageCostBasisCalculator());

        assertThat(account.cash()).isEqualByComparingTo("100000");
        assertThat(account.position(PGR)).isEmpty();
    }

    @Test
    @DisplayName("Non-REJECTED ledger entry without size is rejected")
    void nonRejectedRequiresSize() {
        assertThatThrownBy(() -> new LedgerEntry(
                        OrderId.of(1L),
                        PGR,
                        OrderSide.BUY,
                        OrderType.MARKET,
                        OrderStatus.FILLED,
                        null,
                        new BigDecimal("50"),
                        T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    private static LedgerEntry cashIn() {
        return new LedgerEntry(
                OrderId.of(1L),
                InstrumentId.of(66L),
                OrderSide.CASH_IN,
                OrderType.MARKET,
                OrderStatus.FILLED,
                new BigDecimal("100000"),
                BigDecimal.ONE,
                T0);
    }
}
