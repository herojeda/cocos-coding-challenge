package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.order.Order;
import java.math.BigDecimal;
import java.time.Instant;

/** Registered order representation (ADR-010); unused fields are explicit {@code null}. */
record PlaceOrderResponse(
        long id,
        long instrumentId,
        String side,
        String type,
        Long size,
        BigDecimal amount,
        BigDecimal price,
        String status,
        String rejectionReason,
        Instant datetime) {

    static PlaceOrderResponse from(Order order) {
        return new PlaceOrderResponse(
                order.id().value(),
                order.instrumentId().value(),
                order.side().name(),
                order.type().name(),
                order.size(),
                order.amount(),
                order.price(),
                order.status().name(),
                order.rejectionReason() == null ? null : order.rejectionReason().name(),
                order.datetime());
    }
}
