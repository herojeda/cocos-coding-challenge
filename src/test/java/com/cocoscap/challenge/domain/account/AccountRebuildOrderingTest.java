package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class AccountRebuildOrderingTest {

    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("Rebuild fails when a ledger entry arrives out of order")
    void rebuildRejectsOutOfOrderEntries() {
        Instant earlier = Instant.parse("2023-07-12T13:00:00Z");
        Instant later = earlier.plusSeconds(3600);
        LedgerEntry first = entry(1, earlier);
        LedgerEntry second = entry(2, later);

        assertThatThrownBy(
                        () -> Account.rebuildFrom(
                                AccountId.of(1L), Stream.of(second, first), COST_BASIS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ascending");
    }

    private static LedgerEntry entry(long id, Instant at) {
        return new LedgerEntry(
                OrderId.of(id),
                InstrumentId.of(66L),
                OrderSide.CASH_IN,
                OrderType.MARKET,
                OrderStatus.FILLED,
                new BigDecimal("1000"),
                BigDecimal.ONE,
                at);
    }
}
