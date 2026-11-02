package com.cocoscap.challenge.application.observability;

import java.time.Duration;

/** Business metrics for trading flows (ADR-009). No high-cardinality tags. */
public interface TradingMetricsPort {

    void projectionRebuilt(Duration duration, boolean success);
}
