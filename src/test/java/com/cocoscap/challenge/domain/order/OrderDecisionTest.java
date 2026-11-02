package com.cocoscap.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("OrderDecision becomes a registered Order only after insert assigns an id")
    void toOrderAttachesPersistedId() {
        OrderDecision decision = OrderDecision.accepted(
                USER, GGAL, OrderSide.BUY, OrderType.LIMIT, 10L, new BigDecimal("850.00"), null);

        assertThat(decision.status()).isEqualTo(OrderStatus.NEW);

        Order order = decision.toOrder(OrderId.of(42L), PLACED_AT);

        assertThat(order.id()).isEqualTo(OrderId.of(42L));
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.size()).isEqualTo(10L);
        assertThat(order.price()).isEqualByComparingTo("850.00");
        assertThat(order.datetime()).isEqualTo(PLACED_AT);
    }
}
