package com.cocoscap.challenge.application.observability;

import java.time.Duration;

/** Business metrics for trading flows (ADR-009). No high-cardinality tags. */
public interface TradingMetricsPort {

    void projectionRebuilt(Duration duration, boolean success);

    /** {@code trading.orders}: {@code rejectionReason} is {@code NONE} when the order was not rejected. */
    void orderProcessed(String type, String side, String status, String rejectionReason);

    /** {@code trading.account.busy}: {@code operation} is a closed set (e.g. {@code place}). */
    void accountBusy(String operation);

    /** {@code trading.idempotency.replays}: {@code result} is {@code replayed} or {@code key_reused}. */
    void idempotencyReplay(String result);
}
