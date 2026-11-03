package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.domain.order.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Registered order representation (ADR-010); unused fields are explicit {@code null}. */
@Schema(description = "Registered order; unused fields are explicit null")
record OrderResponse(
        long id,
        long instrumentId,
        @Schema(allowableValues = {"BUY", "SELL"}) String side,
        @Schema(allowableValues = {"MARKET", "LIMIT"}) String type,
        Long size,
        BigDecimal amount,
        BigDecimal price,
        @Schema(allowableValues = {"NEW", "FILLED", "REJECTED", "CANCELLED"}) String status,
        @Schema(allowableValues = {"INSUFFICIENT_CASH", "INSUFFICIENT_HOLDINGS", "NO_MARKET_PRICE"}, nullable = true)
                String rejectionReason,
        Instant datetime) {

    static OrderResponse from(Order order) {
        return new OrderResponse(
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
