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
}
