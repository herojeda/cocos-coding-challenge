package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.order.NewOrder;
import com.cocoscap.challenge.domain.user.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Placement body (ADR-010). Combination rules are validated by {@link NewOrder}. */
@Schema(description = "Order placement body. size and amount are mutually exclusive; price only for LIMIT.")
record PlaceOrderRequest(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long instrumentId,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlaceOrderSideRequest side,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlaceOrderTypeRequest type,
        @Schema(description = "Share quantity; exclusive with amount") Long size,
        @Schema(description = "Cash amount; exclusive with size") BigDecimal amount,
        @Schema(description = "Limit price; required for LIMIT, forbidden for MARKET") BigDecimal price) {

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
