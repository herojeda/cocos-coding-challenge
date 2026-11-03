package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.order.OrderSide;

/** Body {@code side} values for {@code POST /users/{userId}/orders}. */
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
