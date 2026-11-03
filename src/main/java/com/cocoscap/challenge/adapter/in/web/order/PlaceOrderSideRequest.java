package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.order.OrderSide;
import io.swagger.v3.oas.annotations.media.Schema;

/** Body {@code side} values for {@code POST /users/{userId}/orders}. */
@Schema(enumAsRef = true)
enum PlaceOrderSideRequest {
    BUY,
    SELL;

    OrderSide toDomain() {
        return switch (this) {
            case BUY -> OrderSide.BUY;
            case SELL -> OrderSide.SELL;
        };
    }
}
