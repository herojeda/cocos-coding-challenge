package com.cocoscap.challenge.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
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

class AccountRebuildGuardsTest {

    private static final AccountId ACCOUNT = AccountId.of(1L);
    private static final InstrumentId GGAL = InstrumentId.of(34L);
    private static final InstrumentId ARS = InstrumentId.of(66L);
    private static final Instant T0 = Instant.parse("2023-07-12T13:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Test
    @DisplayName("Rebuild accepts a REJECTED MARKET order without price")
    void rebuildAcceptsRejectedWithoutPrice() {
        Account account = Account.rebuildFrom(
                ACCOUNT,
                Stream.of(
                        entry(1, ARS, OrderSide.CASH_IN, OrderType.MARKET, OrderStatus.FILLED, "100000", "1", T0),
                        entry(2, GGAL, OrderSide.BUY, OrderType.MARKET, OrderStatus.REJECTED, "10", null, T1)),
                COST_BASIS);

        assertThat(account.cash()).isEqualByComparingTo("100000");
        assertThat(account.position(GGAL)).isEmpty();
    }

    @Test
    @DisplayName("Rebuild rejects a NEW MARKET order")
    void rebuildRejectsNewMarket() {
        assertThatThrownBy(() -> Account.rebuildFrom(
                        ACCOUNT,
                        Stream.of(entry(
                                1, GGAL, OrderSide.BUY, OrderType.MARKET, OrderStatus.NEW, "10", "1000", T0)),
                        COST_BASIS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIMIT");
    }

    @Test
    @DisplayName("Rebuild rejects a NEW cash transfer")
    void rebuildRejectsNewCashTransfer() {
        assertThatThrownBy(() -> Account.rebuildFrom(
                        ACCOUNT,
                        Stream.of(entry(
                                1, ARS, OrderSide.CASH_IN, OrderType.LIMIT, OrderStatus.NEW, "1000", "1", T0)),
                        COST_BASIS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cash transfer");
    }

    private static LedgerEntry entry(
            long id,
            InstrumentId instrumentId,
            OrderSide side,
            OrderType type,
            OrderStatus status,
            String size,
            String price,
            Instant at) {
        return new LedgerEntry(
                OrderId.of(id),
                instrumentId,
                side,
                type,
                status,
                new BigDecimal(size),
                price == null ? null : new BigDecimal(price),
                at);
    }
}
