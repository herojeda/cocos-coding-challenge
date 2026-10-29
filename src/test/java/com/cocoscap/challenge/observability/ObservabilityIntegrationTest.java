package com.cocoscap.challenge.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics(export = false)
@AutoConfigureTracing(export = false)
@Import({PostgresTestcontainersConfiguration.class, ObservabilityIntegrationTest.ObservationTestConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest {

    private static final String BIND_SECRET = "secret-bind-value-xyz";

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TestObservationRegistry observationRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationTestConfig {

        @Bean
        @Primary
        TestObservationRegistry testObservationRegistry() {
            return TestObservationRegistry.create();
        }
    }

    @Test
    @DisplayName("an HTTP request records http.server.requests")
    void httpRequestRecordsServerMetric() throws Exception {
        mockMvc.perform(get("/actuator/health")).andReturn();

        assertThat(meterRegistry.find("http.server.requests").timers()).isNotEmpty();
    }

    @Test
    @DisplayName("a JDBC query is observed without bind parameter values")
    void jdbcQueryIsObservedWithoutParameterValues() {
        observationRegistry.clear();

        Integer count = jdbcClient
                .sql("SELECT COUNT(*) FROM users WHERE email = ?")
                .param(BIND_SECRET)
                .query(Integer.class)
                .single();
        assertThat(count).isZero();

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("jdbc.query")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasHighCardinalityKeyValue(
                        "jdbc.query[0]", "SELECT COUNT(*) FROM users WHERE email = ?")
                .doesNotHaveHighCardinalityKeyValueWithKey("jdbc.params[0]");

        assertThat(observationRegistry.toString()).doesNotContain(BIND_SECRET);
    }

    @Test
    @DisplayName("a log line during a request is ECS JSON and includes the trace id")
    void requestLogIsEcsJsonWithTraceId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/no-existe")).andReturn();

        String ecsLine = Arrays.stream(output.getOut().split("\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("{"))
                .filter(line -> line.contains("UNSUPPORTED_REQUEST") || line.contains("Request failed"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Expected an ECS JSON log line for the request error"));

        JsonNode json = objectMapper.readTree(ecsLine);
        assertThat(json.isObject()).isTrue();
        assertThat(json.path("trace.id").asString(null))
                .as("ECS log should carry trace.id")
                .isNotBlank();
        assertThat(json.path("span.id").asString(null))
                .as("ECS log should carry span.id")
                .isNotBlank();
    }

    @Test
    @DisplayName("error advice attaches code and instance to the HTTP observation, not metric tags")
    void errorAdviceAttachesCodeAndInstanceToObservationNotMetrics() throws Exception {
        observationRegistry.clear();

        mockMvc.perform(get("/no-existe")).andReturn();

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("http.server.requests")
                .that()
                .hasHighCardinalityKeyValue("error.code", "UNSUPPORTED_REQUEST")
                .hasHighCardinalityKeyValueWithKey("error.instance");

        assertThat(meterRegistry.find("http.server.requests").meters())
                .isNotEmpty()
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("error.code")
                                || tag.getKey().equals("error.instance")
                                || tag.getKey().equals("instance")));
    }
}
