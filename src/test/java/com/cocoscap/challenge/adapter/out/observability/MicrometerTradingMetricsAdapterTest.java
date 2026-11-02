package com.cocoscap.challenge.adapter.out.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrometerTradingMetricsAdapterTest {

    @Test
    @DisplayName("projection rebuilds timer records success and failure outcomes")
    void recordsSuccessAndFailureOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTradingMetricsAdapter metrics = new MicrometerTradingMetricsAdapter(registry);

        metrics.projectionRebuilt(Duration.ofMillis(12), true);
        metrics.projectionRebuilt(Duration.ofMillis(8), false);

        assertThat(registry
                        .find("trading.projection.rebuilds")
                        .tag("outcome", "success")
                        .timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
        assertThat(registry
                        .find("trading.projection.rebuilds")
                        .tag("outcome", "failure")
                        .timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }

    @Test
    @DisplayName("order and busy counters publish ADR-009 tags")
    void recordsOrderAndBusyCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTradingMetricsAdapter metrics = new MicrometerTradingMetricsAdapter(registry);

        metrics.orderProcessed("MARKET", "BUY", "FILLED", "NONE");
        metrics.accountBusy("place");
        metrics.idempotencyReplay("replayed");

        assertThat(registry
                        .find("trading.orders")
                        .tag("type", "MARKET")
                        .tag("side", "BUY")
                        .tag("status", "FILLED")
                        .tag("rejection_reason", "NONE")
                        .counter())
                .isNotNull()
                .satisfies(counter -> assertThat(counter.count()).isEqualTo(1));
        assertThat(registry.find("trading.account.busy").tag("operation", "place").counter())
                .isNotNull()
                .satisfies(counter -> assertThat(counter.count()).isEqualTo(1));
        assertThat(registry.find("trading.idempotency.replays").tag("result", "replayed").counter())
                .isNotNull()
                .satisfies(counter -> assertThat(counter.count()).isEqualTo(1));
    }
}
