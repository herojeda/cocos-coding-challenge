package com.cocoscap.challenge.adapter.out.observability;

import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;

public final class MicrometerTradingMetricsAdapter implements TradingMetricsPort {

    private static final String PROJECTION_REBUILDS = "trading.projection.rebuilds";
    private static final String ORDERS = "trading.orders";
    private static final String ACCOUNT_BUSY = "trading.account.busy";
    private static final String IDEMPOTENCY_REPLAYS = "trading.idempotency.replays";

    private final MeterRegistry meterRegistry;

    public MicrometerTradingMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void projectionRebuilt(Duration duration, boolean success) {
        Objects.requireNonNull(duration, "duration");
        Timer.builder(PROJECTION_REBUILDS)
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .record(duration);
    }

    @Override
    public void orderProcessed(String type, String side, String status, String rejectionReason) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(rejectionReason, "rejectionReason");
        meterRegistry
                .counter(
                        ORDERS,
                        "type",
                        type,
                        "side",
                        side,
                        "status",
                        status,
                        "rejection_reason",
                        rejectionReason)
                .increment();
    }

    @Override
    public void accountBusy(String operation) {
        Objects.requireNonNull(operation, "operation");
        meterRegistry.counter(ACCOUNT_BUSY, "operation", operation).increment();
    }

    @Override
    public void idempotencyReplay(String result) {
        Objects.requireNonNull(result, "result");
        meterRegistry.counter(IDEMPOTENCY_REPLAYS, "result", result).increment();
    }
}
