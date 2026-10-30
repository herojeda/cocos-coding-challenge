package com.cocoscap.challenge.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

class OtlpExportConfigurationTest {

    private static final String OTLP_METER_REGISTRY = "io.micrometer.registry.otlp.OtlpMeterRegistry";
    private static final String OTLP_HTTP_SPAN_EXPORTER =
            "io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter";

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Expected OTLP type on the runtime classpath: " + className, e);
        }
    }

    private static String[] beanNamesFor(ApplicationContext context, String className) {
        return context.getBeanNamesForType(load(className));
    }

    @Nested
    @SpringBootTest
    @Import(PostgresTestcontainersConfiguration.class)
    class DefaultProfile {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("default config does not register OTLP meter or span exporters")
        void defaultConfigDoesNotRegisterOtlpExporters() {
            assertThat(beanNamesFor(applicationContext, OTLP_METER_REGISTRY)).isEmpty();
            assertThat(beanNamesFor(applicationContext, OTLP_HTTP_SPAN_EXPORTER)).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = {
        // close() still publishes once; a long step avoids a mid-test export, and sampling 0
        // exports no spans. The registry logger is off because that final publish has no collector.
        "management.otlp.metrics.export.step=24h",
        "management.tracing.sampling.probability=0",
        "logging.level.io.micrometer.registry.otlp.OtlpMeterRegistry=OFF"
    })
    @ActiveProfiles("observability")
    @Import(PostgresTestcontainersConfiguration.class)
    class ObservabilityProfile {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("observability profile registers OTLP meter and span exporters")
        void observabilityProfileRegistersOtlpExporters() {
            assertThat(beanNamesFor(applicationContext, OTLP_METER_REGISTRY)).isNotEmpty();
            assertThat(beanNamesFor(applicationContext, OTLP_HTTP_SPAN_EXPORTER)).isNotEmpty();
        }
    }
}
