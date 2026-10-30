package com.cocoscap.challenge.adapter.in.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(controllers = ErrorHandlingTestController.class)
@Import({ApiExceptionHandler.class, NosniffHeaderFilter.class})
class ApiExceptionHandlerTest {

    private static final String XSS_PAYLOAD = "<script>alert(1)</script>";
    private static final String SECRET = "secret-internal-detail";

    @Autowired
    private MockMvcTester mvc;

    @ParameterizedTest(name = "{0}")
    @MethodSource("mappedErrors")
    void mapsSpringMvcExceptionsToProblemDetails(
            String scenario, RunnableExchange exchange, HttpStatus status, ProblemCode code) {
        MvcTestResult result = exchange.perform(mvc);

        assertThat(result).hasStatus(status);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(jsonString(result, "$.code")).isEqualTo(code.name());
        assertThat(jsonString(result, "$.type")).isEqualTo(code.typeUri());
        assertThat(jsonString(result, "$.title")).isEqualTo(code.title());
        assertThat(jsonString(result, "$.detail")).isEqualTo(code.detail());
        assertThat(jsonInt(result, "$.status")).isEqualTo(status.value());
    }

    static Stream<Arguments> mappedErrors() {
        return Stream.of(
                Arguments.of(
                        "malformed JSON body",
                        (RunnableExchange) mvc -> mvc.post()
                                .uri("/__test__/errors/bodies")
                                .header("X-Required", "present")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not-json")
                                .exchange(),
                        HttpStatus.BAD_REQUEST,
                        ProblemCode.MALFORMED_REQUEST),
                Arguments.of(
                        "invalid body fails bean validation",
                        (RunnableExchange) mvc -> mvc.post()
                                .uri("/__test__/errors/bodies")
                                .header("X-Required", "present")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":0,\"label\":\"ok\"}")
                                .exchange(),
                        HttpStatus.BAD_REQUEST,
                        ProblemCode.VALIDATION_ERROR),
                Arguments.of(
                        "missing required header",
                        (RunnableExchange) mvc -> mvc.post()
                                .uri("/__test__/errors/bodies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":1,\"label\":\"ok\"}")
                                .exchange(),
                        HttpStatus.BAD_REQUEST,
                        ProblemCode.VALIDATION_ERROR),
                Arguments.of(
                        "non-numeric path variable",
                        (RunnableExchange) mvc -> mvc.get().uri("/__test__/errors/items/abc").exchange(),
                        HttpStatus.BAD_REQUEST,
                        ProblemCode.VALIDATION_ERROR),
                Arguments.of(
                        "unknown path",
                        (RunnableExchange) mvc -> mvc.get().uri("/__test__/errors/does-not-exist").exchange(),
                        HttpStatus.NOT_FOUND,
                        ProblemCode.UNSUPPORTED_REQUEST),
                Arguments.of(
                        "method not allowed",
                        (RunnableExchange) mvc -> mvc.post().uri("/__test__/errors/ok").exchange(),
                        HttpStatus.METHOD_NOT_ALLOWED,
                        ProblemCode.UNSUPPORTED_REQUEST),
                Arguments.of(
                        "unsupported media type",
                        (RunnableExchange) mvc -> mvc.post()
                                .uri("/__test__/errors/bodies")
                                .header("X-Required", "present")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("amount=1")
                                .exchange(),
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        ProblemCode.UNSUPPORTED_REQUEST),
                Arguments.of(
                        "unhandled exception",
                        (RunnableExchange) mvc -> mvc.get().uri("/__test__/errors/boom").exchange(),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ProblemCode.INTERNAL_ERROR));
    }

    @Test
    @DisplayName("VALIDATION_ERROR lists field errors for invalid body")
    void validationErrorListsBodyFieldErrors() {
        MvcTestResult result = mvc.post()
                .uri("/__test__/errors/bodies")
                .header("X-Required", "present")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0,\"label\":\"\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(result, "$.code")).isEqualTo("VALIDATION_ERROR");
        String body = responseBody(result);
        assertThat(body).contains("\"field\":\"amount\"");
        assertThat(body).contains("\"reason\":\"must be greater than 0\"");
        assertThat(body).contains("\"field\":\"label\"");
        assertThat(body).contains("\"reason\":\"is required\"");
    }

    @Test
    @DisplayName("VALIDATION_ERROR lists missing header")
    void validationErrorListsMissingHeader() {
        MvcTestResult result = mvc.post()
                .uri("/__test__/errors/bodies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1,\"label\":\"ok\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo("X-Required");
        assertThat(jsonString(result, "$.errors[0].reason")).isEqualTo("is required");
    }

    @Test
    @DisplayName("VALIDATION_ERROR lists non-numeric path variable")
    void validationErrorListsInvalidPathVariable() {
        MvcTestResult result = mvc.get().uri("/__test__/errors/items/abc").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo("id");
        assertThat(jsonString(result, "$.errors[0].reason")).isEqualTo("has an invalid type");
    }

    @Test
    @DisplayName("rejected input is not reflected in the problem body")
    void rejectedInputIsNotReflected() {
        MvcTestResult bodyResult = mvc.post()
                .uri("/__test__/errors/bodies")
                .header("X-Required", "present")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0,\"label\":\"" + XSS_PAYLOAD + "\"}")
                .exchange();
        assertThat(responseBody(bodyResult)).doesNotContain(XSS_PAYLOAD);

        MvcTestResult pathResult = mvc.get().uri("/__test__/errors/items/{id}", XSS_PAYLOAD).exchange();
        assertThat(responseBody(pathResult)).doesNotContain(XSS_PAYLOAD);
    }

    @Test
    @DisplayName("INTERNAL_ERROR does not leak exception message")
    void internalErrorDoesNotLeakExceptionMessage() {
        MvcTestResult result = mvc.get().uri("/__test__/errors/boom").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(jsonString(result, "$.code")).isEqualTo("INTERNAL_ERROR");
        assertThat(responseBody(result)).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("instance is a urn:uuid and changes between errors")
    void instanceIsUuidUrnAndUnique() {
        MvcTestResult first = mvc.get().uri("/__test__/errors/does-not-exist").exchange();
        MvcTestResult second = mvc.get().uri("/__test__/errors/does-not-exist").exchange();

        String firstInstance = jsonString(first, "$.instance");
        String secondInstance = jsonString(second, "$.instance");

        assertThat(firstInstance).matches("urn:uuid:[0-9a-fA-F-]{36}");
        assertThat(secondInstance).matches("urn:uuid:[0-9a-fA-F-]{36}");
        assertThat(firstInstance).isNotEqualTo(secondInstance);
    }

    @Test
    @DisplayName("nosniff header is present on success and error responses")
    void nosniffHeaderPresentOnSuccessAndError() {
        MvcTestResult success = mvc.get().uri("/__test__/errors/ok").exchange();
        MvcTestResult error = mvc.get().uri("/__test__/errors/does-not-exist").exchange();

        assertThat(success.getResponse().getHeader(NosniffHeaderFilter.HEADER_NAME))
                .isEqualTo(NosniffHeaderFilter.HEADER_VALUE);
        assertThat(error.getResponse().getHeader(NosniffHeaderFilter.HEADER_NAME))
                .isEqualTo(NosniffHeaderFilter.HEADER_VALUE);
    }

    private static String responseBody(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String jsonString(MvcTestResult result, String path) {
        return JsonPath.read(responseBody(result), path);
    }

    private static int jsonInt(MvcTestResult result, String path) {
        Number value = JsonPath.read(responseBody(result), path);
        return value.intValue();
    }

    @FunctionalInterface
    interface RunnableExchange {
        MvcTestResult perform(MockMvcTester mvc);
    }
}
