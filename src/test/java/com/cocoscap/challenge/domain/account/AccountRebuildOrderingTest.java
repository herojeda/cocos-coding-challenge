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

class AccountRebuildOrderingTest {

    @Test
    @DisplayName("Rebuild result does not depend on input order")
    void rebuildIsOrderIndependent() {
        LocalDateTime t0 = LocalDateTime.of(2023, 7, 12, 10, 0);
        LedgerEntry cashIn = entry(1, 66, OrderSide.CASH_IN, OrderStatus.FILLED, "100000", "1", t0);
        LedgerEntry buy = entry(2, 34, OrderSide.BUY, OrderStatus.FILLED, "10", "1000", t0.plusHours(1));
        LedgerEntry pending =
                new LedgerEntry(
                        3L, 34L, OrderSide.BUY, OrderType.LIMIT, OrderStatus.NEW,
                        new BigDecimal("5"), new BigDecimal("900"), t0.plusHours(2));

        Account chronological = Account.rebuildFrom(List.of(cashIn, buy, pending));
        Account shuffled = Account.rebuildFrom(List.of(pending, buy, cashIn));

        assertThat(shuffled.cash()).isEqualByComparingTo(chronological.cash());
        assertThat(shuffled.reservedCash()).isEqualByComparingTo(chronological.reservedCash());
        assertThat(shuffled.availableQuantity(34L)).isEqualTo(chronological.availableQuantity(34L));
    }

    private static LedgerEntry entry(
            long id, long instrumentId, OrderSide side, OrderStatus status, String size, String price, LocalDateTime at) {
        return new LedgerEntry(
                id, instrumentId, side, OrderType.MARKET, status, new BigDecimal(size), new BigDecimal(price), at);
    }
}
