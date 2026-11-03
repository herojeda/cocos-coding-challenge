package com.cocoscap.challenge.domain.order;

/** Raised when an order does not exist for the requesting user (S11). */
public final class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("order not found");
    }
}
