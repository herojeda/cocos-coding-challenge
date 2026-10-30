package com.cocoscap.challenge.domain.account;

/**
 * Holdings for one instrument. Does not enforce non-negative quantity (seed short BMA / S3)
 * or available quantity — order policy does that later.
 */
public final class Position {

    private final long instrumentId;
    private long quantity;
    private long reservedQuantity;

    Position(long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public long instrumentId() {
        return instrumentId;
    }

    public long quantity() {
        return quantity;
    }

    public long reservedQuantity() {
        return reservedQuantity;
    }

    public long availableQuantity() {
        return quantity - reservedQuantity;
    }

    void applyBuy(long quantity) {
        requirePositive(quantity);
        this.quantity = Math.addExact(this.quantity, quantity);
    }

    void applySell(long quantity) {
        requirePositive(quantity);
        this.quantity = Math.subtractExact(this.quantity, quantity);
    }

    void reserve(long quantity) {
        requirePositive(quantity);
        this.reservedQuantity = Math.addExact(this.reservedQuantity, quantity);
    }

    void release(long quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new IllegalStateException("reserved quantity would become negative");
        }
        this.reservedQuantity -= quantity;
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
