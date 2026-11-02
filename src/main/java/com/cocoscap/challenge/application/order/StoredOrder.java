package com.cocoscap.challenge.application.order;

import com.cocoscap.challenge.domain.order.Order;
import com.cocoscap.challenge.domain.order.RequestHash;
import java.util.Objects;

/** Order row loaded for idempotency comparison (ADR-008): registered order plus its fingerprint. */
public record StoredOrder(Order order, RequestHash requestHash) {

    public StoredOrder {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(requestHash, "requestHash");
    }
}
