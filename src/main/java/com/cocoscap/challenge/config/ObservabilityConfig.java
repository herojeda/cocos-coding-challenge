package com.cocoscap.challenge.config;

import com.cocoscap.challenge.adapter.out.observability.MicrometerTradingMetricsAdapter;
import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    TradingMetricsPort tradingMetricsPort(MeterRegistry meterRegistry) {
        return new MicrometerTradingMetricsAdapter(meterRegistry);
    }
}
