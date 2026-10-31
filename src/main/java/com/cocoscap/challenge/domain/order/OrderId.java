package com.cocoscap.challenge.domain.order;

/** Identity of a ledger order. */
public record OrderId(long value) {

    public OrderId {
        if (value <= 0) {
            throw new IllegalArgumentException("OrderId must be positive");
        }
    }

    public static OrderId of(long value) {
        return new OrderId(value);
    }
}
