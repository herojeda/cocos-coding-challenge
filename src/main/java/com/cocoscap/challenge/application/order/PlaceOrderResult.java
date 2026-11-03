package com.cocoscap.challenge.application.order;

import com.cocoscap.challenge.domain.order.Order;
import java.util.Objects;

/** Outcome of {@link PlaceOrderUseCase}: a newly registered order or an idempotent replay. */
public sealed interface PlaceOrderResult {

    Order order();

    record Registered(Order order) implements PlaceOrderResult {
        public Registered {
            Objects.requireNonNull(order, "order");
        }
    }

    record Replayed(Order order) implements PlaceOrderResult {
        public Replayed {
            Objects.requireNonNull(order, "order");
        }
    }
}
