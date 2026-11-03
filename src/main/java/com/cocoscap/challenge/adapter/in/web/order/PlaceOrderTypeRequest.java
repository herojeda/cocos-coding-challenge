package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.order.OrderType;

/** Body {@code type} values for {@code POST /users/{userId}/orders}. */
enum PlaceOrderTypeRequest {
    MARKET,
    LIMIT;

    OrderType toDomain() {
        return switch (this) {
            case MARKET -> OrderType.MARKET;
            case LIMIT -> OrderType.LIMIT;
        };
    }
}
