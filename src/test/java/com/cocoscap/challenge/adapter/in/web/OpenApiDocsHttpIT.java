package com.cocoscap.challenge.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class OpenApiDocsHttpIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("OpenAPI documents the four API paths and excludes actuator")
    void apiDocsPaths() {
        Map<String, Object> paths = JsonPath.read(apiDocs(), "$.paths");
        assertThat(paths.keySet())
                .containsExactlyInAnyOrder(
                        "/instruments",
                        "/users/{userId}/portfolio",
                        "/users/{userId}/orders",
                        "/users/{userId}/orders/{orderId}/cancel");
        assertThat(paths.keySet()).noneMatch(path -> path.startsWith("/actuator"));
    }

    @Test
    @DisplayName("OpenAPI requires Idempotency-Key and documents 201 and 200 for placement")
    void placeOrderIdempotencyAndStatuses() {
        String docs = apiDocs();
        List<Map<String, Object>> parameters =
                JsonPath.read(docs, "$.paths['/users/{userId}/orders'].post.parameters");
        assertThat(parameters).anySatisfy(parameter -> {
            assertThat(parameter.get("name")).isEqualTo("Idempotency-Key");
            assertThat(parameter.get("in")).isEqualTo("header");
            assertThat(parameter.get("required")).isEqualTo(true);
        });
        Map<String, Object> responses = JsonPath.read(docs, "$.paths['/users/{userId}/orders'].post.responses");
        assertThat(responses).containsKeys("201", "200");
    }

    @Test
    @DisplayName("OpenAPI place-order schema uses snake_case instrument_id")
    void placeOrderSchemaSnakeCase() {
        String docs = apiDocs();
        String ref = JsonPath.read(
                docs, "$.paths['/users/{userId}/orders'].post.requestBody.content['application/json'].schema.$ref");
        String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
        Map<String, Object> properties =
                JsonPath.read(docs, "$.components.schemas['" + schemaName + "'].properties");
        assertThat(properties).containsKey("instrument_id").doesNotContainKey("instrumentId");
    }

    @Test
    @DisplayName("OpenAPI ProblemDetail documents errors as an array of field/reason")
    void problemDetailErrorsIsArray() {
        String docs = apiDocs();
        assertThat(JsonPath.<String>read(docs, "$.components.schemas.ProblemDetail.properties.errors.type"))
                .isEqualTo("array");
        assertThat(JsonPath.<Object>read(
                        docs, "$.components.schemas.ProblemDetail.properties.errors.items.properties.field"))
                .isNotNull();
        assertThat(JsonPath.<Object>read(
                        docs, "$.components.schemas.ProblemDetail.properties.errors.items.properties.reason"))
                .isNotNull();
    }

    @Test
    @DisplayName("Swagger UI is reachable at /swagger-ui.html")
    void swaggerUiReachable() {
        MvcTestResult result = mvc.get().uri("/swagger-ui.html").exchange();
        assertThat(result.getResponse().getStatus())
                .isIn(HttpStatus.OK.value(), HttpStatus.FOUND.value(), HttpStatus.MOVED_PERMANENTLY.value());
    }

    private String apiDocs() {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
