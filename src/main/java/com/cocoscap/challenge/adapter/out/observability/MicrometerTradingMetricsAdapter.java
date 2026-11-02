package com.cocoscap.challenge.adapter.out.observability;

import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;

public final class MicrometerTradingMetricsAdapter implements TradingMetricsPort {

    private static final String PROJECTION_REBUILDS = "trading.projection.rebuilds";

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
}
