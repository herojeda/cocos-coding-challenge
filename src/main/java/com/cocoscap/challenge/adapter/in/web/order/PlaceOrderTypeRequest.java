package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.order.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Body {@code type} values for {@code POST /users/{userId}/orders}. */
@Schema(enumAsRef = true)
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
