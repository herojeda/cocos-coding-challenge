package com.cocoscap.challenge.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import io.micrometer.common.KeyValue;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "cocos.test.server-error-probe=true")
@AutoConfigureMockMvc
@AutoConfigureMetrics(export = false)
@AutoConfigureTracing(export = false)
@Import({
    PostgresTestcontainersConfiguration.class,
    ObservabilityIntegrationTest.ObservationTestConfig.class,
    ObservabilityIntegrationTest.BoomController.class
})
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest {

    private static final String BIND_SECRET = "secret-bind-value-xyz";
    private static final String SERVER_FAILURE_SECRET = "secret-internal-detail";

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

    @RestController
    @ConditionalOnProperty(name = "cocos.test.server-error-probe", havingValue = "true")
    static class BoomController {

        @GetMapping("/__test__/observability/boom")
        void boom() {
            throw new IllegalStateException(SERVER_FAILURE_SECRET);
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
    @DisplayName("a 4xx leaves problem code and instance on the HTTP observation, not on metric tags")
    void clientErrorAttachesProblemAttributesWithoutMarkingTheObservationFailed() throws Exception {
        observationRegistry.clear();

        MvcResult result = mockMvc.perform(get("/no-existe")).andReturn();
        String instance = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("instance")
                .asString();

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("http.server.requests")
                .that()
                .doesNotHaveError()
                .hasHighCardinalityKeyValue("problem.code", "UNSUPPORTED_REQUEST")
                .hasHighCardinalityKeyValue("problem.instance", instance);

        assertProblemAttributesAreNotMetricTags();
    }

    @Test
    @DisplayName("a 500 marks the HTTP observation as failed without copying the exception text")
    void serverErrorMarksObservationFailedWithoutLeakingExceptionText() throws Exception {
        observationRegistry.clear();

        MvcResult result = mockMvc.perform(get("/__test__/observability/boom")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("detail").asString()).doesNotContain(SERVER_FAILURE_SECRET);
        String instance = body.path("instance").asString();

        TestObservationRegistryAssert.assertThat(observationRegistry)
                .hasObservationWithNameEqualTo("http.server.requests")
                .that()
                .hasError()
                .hasHighCardinalityKeyValue("problem.code", "INTERNAL_ERROR")
                .hasHighCardinalityKeyValue("problem.instance", instance)
                .satisfies(context -> {
                    assertThat(context.getError()).isInstanceOf(IllegalStateException.class);
                    assertThat(context.getAllKeyValues())
                            .noneMatch(keyValue -> containsSecret(keyValue));
                });

        assertProblemAttributesAreNotMetricTags();
    }

    private void assertProblemAttributesAreNotMetricTags() {
        assertThat(meterRegistry.find("http.server.requests").meters())
                .isNotEmpty()
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("problem.code")
                                || tag.getKey().equals("problem.instance")
                                || tag.getKey().equals("instance")
                                || tag.getValue().contains(SERVER_FAILURE_SECRET)));
    }

    private static boolean containsSecret(KeyValue keyValue) {
        return keyValue.getKey().contains(SERVER_FAILURE_SECRET)
                || keyValue.getValue().contains(SERVER_FAILURE_SECRET);
    }
}
