package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.NewOrder;
import com.cocoscap.challenge.domain.user.UserId;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Placement body (ADR-010). Combination rules are validated by {@link NewOrder}. */
record PlaceOrderRequest(
        @NotNull Long instrumentId,
        @NotNull PlaceOrderSideRequest side,
        @NotNull PlaceOrderTypeRequest type,
        Long size,
        BigDecimal amount,
        BigDecimal price) {

    NewOrder toNewOrder(UserId userId) {
        return new NewOrder(
                userId,
                InstrumentId.of(instrumentId),
                side.toDomain(),
                type.toDomain(),
                size,
                amount,
                price);
    }
}
