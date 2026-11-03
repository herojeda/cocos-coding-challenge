package com.cocoscap.challenge.domain.order;

import java.util.Objects;

/** Raised when cancel is requested for an order that is not in {@link OrderStatus#NEW}. */
public final class OrderNotCancellableException extends RuntimeException {

    private final OrderStatus currentStatus;

    public OrderNotCancellableException(OrderStatus currentStatus) {
        super("only orders in status NEW can be cancelled");
        this.currentStatus = Objects.requireNonNull(currentStatus, "currentStatus");
    }

    public OrderStatus currentStatus() {
        return currentStatus;
    }
}
