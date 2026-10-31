package com.cocoscap.challenge.adapter.in.web.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class InstrumentSearchHttpIT {

    private static final List<String> FIRST_PAGE_FOR_A = List.of(
            "AGRO", "ALUA", "AUSO", "BBAR", "BHIP", "BMA", "BOLT", "BPAT", "BYMA", "CADO",
            "CAPX", "CARC", "CECO2", "CELU", "CEPU", "CGPA2", "COME", "CRES", "CTIO", "CVH");

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("AS-06 · Datos de un instrumento encontrado")
    void as06_instrumentJsonShape() {
        MvcTestResult result = get("/instruments?search=YPFD");

        assertThat(result).hasStatusOk();
        assertThat(jsonString(result, "$.instruments[0].ticker")).isEqualTo("YPFD");
        assertThat(jsonString(result, "$.instruments[0].name")).isEqualTo("Y.P.F. S.A.");
        assertThat(jsonString(result, "$.instruments[0].type")).isEqualTo("STOCK");
        assertThat(jsonInt(result, "$.instruments[0].id")).isPositive();
        assertThat(body(result)).contains("\"has_next\"");
        assertThat(body(result)).doesNotContain("\"hasNext\"");
    }

    @Test
    @DisplayName("AS-08 · Búsqueda vacía")
    void as08_blankSearch() {
        MvcTestResult result = mvc.get().uri("/instruments").param("search", "   ").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(jsonString(result, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo("search");
    }

    @Test
    @DisplayName("AS-12 · Primera página por defecto")
    void as12_defaultFirstPage() {
        MvcTestResult result = get("/instruments?search=a");

        assertThat(result).hasStatusOk();
        assertThat(jsonList(result, "$.instruments[*].ticker")).containsExactlyElementsOf(FIRST_PAGE_FOR_A);
        assertThat(jsonInt(result, "$.page.number")).isZero();
        assertThat(jsonInt(result, "$.page.size")).isEqualTo(20);
        assertThat(jsonBool(result, "$.page.has_next")).isTrue();
    }

    @Test
    @DisplayName("AS-13 · Páginas siguientes")
    void as13_followingPages() {
        MvcTestResult first = get("/instruments?search=banco&page=0&size=2");
        MvcTestResult second = get("/instruments?search=banco&page=1&size=2");

        assertThat(jsonList(first, "$.instruments[*].ticker")).containsExactly("BBAR", "BHIP");
        assertThat(jsonBool(first, "$.page.has_next")).isTrue();
        assertThat(jsonList(second, "$.instruments[*].ticker")).containsExactly("BMA", "BPAT");
        assertThat(jsonBool(second, "$.page.has_next")).isFalse();
    }

    @Test
    @DisplayName("AS-14 · Página posterior a la última")
    void as14_pastLastPage() {
        MvcTestResult result = get("/instruments?search=banco&page=2&size=2");

        assertThat(result).hasStatusOk();
        assertThat(jsonList(result, "$.instruments[*].ticker")).isEmpty();
        assertThat(jsonBool(result, "$.page.has_next")).isFalse();
    }

    @Test
    @DisplayName("AS-15 · Tamaño de página fuera de rango")
    void as15_invalidSize() {
        assertValidationError(get("/instruments?search=banco&size=0"), "size");
        assertValidationError(get("/instruments?search=banco&size=101"), "size");
    }

    @Test
    @DisplayName("AS-16 · Número de página negativo")
    void as16_negativePage() {
        assertValidationError(get("/instruments?search=banco&page=-1"), "page");
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private static void assertValidationError(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(result, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo(field);
        assertThat(body(result)).doesNotContain("banco");
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String jsonString(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static int jsonInt(MvcTestResult result, String path) {
        Number value = JsonPath.read(body(result), path);
        return value.intValue();
    }

    private static boolean jsonBool(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static List<String> jsonList(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }
}
