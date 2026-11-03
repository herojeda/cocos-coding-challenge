package com.cocoscap.challenge.application.observability;

/** Closed set of {@code trading.idempotency.replays} outcomes (ADR-009). */
public enum IdempotencyReplayResult {
    REPLAYED,
    KEY_REUSED
}
