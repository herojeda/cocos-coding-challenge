package com.cocoscap.challenge.domain.order;

import com.cocoscap.challenge.domain.instrument.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of {@link OrderPlacementPolicy}: wraps the pre-insert {@link OrderDecision} and exposes
 * helpers for account transitions. Account impact is applied by
 * {@code Account.apply(OrderPlacementDecision)} so domain packages stay acyclic (ADR-003).
 */
public final class OrderPlacementDecision {

    private final OrderDecision order;

    OrderPlacementDecision(OrderDecision order) {
        this.order = Objects.requireNonNull(order, "order");
    }

    public OrderDecision order() {
        return order;
    }

    public OrderStatus status() {
        return order.status();
    }

    public Long size() {
        return order.size();
    }

    public BigDecimal price() {
        return order.price();
    }

    public RejectionReason rejectionReason() {
        return order.rejectionReason();
    }

    public InstrumentId instrumentId() {
        return order.instrumentId();
    }

    public OrderSide side() {
        return order.side();
    }

    public BigDecimal reservationAmount() {
        return BigDecimal.valueOf(order.size()).multiply(order.price());
    }

    public Order toOrder(OrderId id, Instant datetime) {
        return order.toOrder(id, datetime);
    }
}
