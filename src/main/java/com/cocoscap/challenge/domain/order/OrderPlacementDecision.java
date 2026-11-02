package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of {@link OrderPlacementPolicy}: the order to register. Account impact is applied by
 * {@code Account.apply(OrderPlacementDecision)} so domain packages stay acyclic (ADR-003).
 */
public final class OrderPlacementDecision {

    private final NewOrder newOrder;
    private final OrderStatus status;
    private final Long size;
    private final BigDecimal price;
    private final RejectionReason rejectionReason;

    OrderPlacementDecision(
            NewOrder newOrder,
            OrderStatus status,
            Long size,
            BigDecimal price,
            RejectionReason rejectionReason) {
        this.newOrder = Objects.requireNonNull(newOrder, "newOrder");
        this.status = Objects.requireNonNull(status, "status");
        this.size = size;
        this.price = price;
        this.rejectionReason = rejectionReason;
    }

    public OrderStatus status() {
        return status;
    }

    public Long size() {
        return size;
    }

    public BigDecimal price() {
        return price;
    }

    public RejectionReason rejectionReason() {
        return rejectionReason;
    }

    public InstrumentId instrumentId() {
        return newOrder.instrumentId();
    }

    public OrderSide side() {
        return newOrder.side();
    }

    public BigDecimal reservationAmount() {
        return BigDecimal.valueOf(size).multiply(price);
    }

    public Order toOrder(OrderId id, Instant datetime) {
        return switch (status) {
            case FILLED -> Order.filled(
                    id,
                    newOrder.userId(),
                    newOrder.instrumentId(),
                    newOrder.side(),
                    newOrder.type(),
                    size,
                    price,
                    newOrder.amount(),
                    datetime);
            case NEW -> Order.accepted(
                    id,
                    newOrder.userId(),
                    newOrder.instrumentId(),
                    newOrder.side(),
                    newOrder.type(),
                    size,
                    price,
                    newOrder.amount(),
                    datetime);
            case REJECTED -> Order.rejected(
                    id,
                    newOrder.userId(),
                    newOrder.instrumentId(),
                    newOrder.side(),
                    newOrder.type(),
                    size,
                    price,
                    newOrder.amount(),
                    rejectionReason,
                    datetime);
            case CANCELLED -> throw new IllegalStateException("placement cannot produce CANCELLED");
        };
    }
}
