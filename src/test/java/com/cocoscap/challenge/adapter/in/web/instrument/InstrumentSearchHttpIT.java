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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class InstrumentSearchHttpIT {

    private static final List<String> FIRST_PAGE_UNFILTERED = List.of(
            "AGRO", "ALUA", "AUSO", "BBAR", "BHIP", "BMA", "BOLT", "BPAT", "BYMA", "CADO",
            "CAPX", "CARC", "CECO2", "CELU", "CEPU", "CGPA2", "COME", "CRES", "CTIO", "CVH");

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("AS-06 · Datos de un instrumento encontrado")
    void as06_jsonShape() {
        MvcTestResult result = get("/instruments?ticker=YPFD");

        assertThat(result).hasStatusOk();
        assertThat(jsonString(result, "$.instruments[0].ticker")).isEqualTo("YPFD");
        assertThat(jsonString(result, "$.instruments[0].name")).isEqualTo("Y.P.F. S.A.");
        assertThat(jsonString(result, "$.instruments[0].type")).isEqualTo("STOCK");
        assertThat(jsonInt(result, "$.instruments[0].id")).isPositive();
        assertThat(jsonInt(result, "$.page.number")).isZero();
        assertThat(jsonInt(result, "$.page.size")).isEqualTo(20);
        assertThat(body(result)).contains("\"has_next\"");
        assertThat(body(result)).doesNotContain("\"hasNext\"");
    }

    @Test
    @DisplayName("AS-08 · Filtro vacío")
    void as08_blankFilters() {
        assertValidationError(mvc.get().uri("/instruments").param("ticker", "  ").exchange(), "ticker");
        assertValidationError(mvc.get().uri("/instruments").param("name", "").exchange(), "name");
    }

    @Test
    @DisplayName("AS-12 · Primera página por defecto")
    void as12_defaults() {
        MvcTestResult result = get("/instruments");

        assertThat(result).hasStatusOk();
        assertThat(jsonList(result, "$.instruments[*].ticker")).containsExactlyElementsOf(FIRST_PAGE_UNFILTERED);
        assertThat(jsonInt(result, "$.page.number")).isZero();
        assertThat(jsonInt(result, "$.page.size")).isEqualTo(20);
        assertThat(jsonBool(result, "$.page.has_next")).isTrue();
    }

    @Test
    @DisplayName("AS-15 · Tamaño de página fuera de rango")
    void as15_invalidSize() {
        assertValidationError(get("/instruments?size=0"), "size");
        assertValidationError(get("/instruments?size=101"), "size");
    }

    @Test
    @DisplayName("AS-16 · Número de página negativo")
    void as16_negativePage() {
        assertValidationError(get("/instruments?page=-1"), "page");
    }

    @Test
    @DisplayName("AS-22 · Orden por ticker")
    void as22_defaultSortIsTicker() {
        MvcTestResult result = get("/instruments?name=grupo");

        assertThat(result).hasStatusOk();
        assertThat(jsonList(result, "$.instruments[*].ticker"))
                .containsExactly("GCLA", "GGAL", "OEST", "SUPV");
    }

    @Test
    @DisplayName("AS-23 · Orden por nombre (HTTP)")
    void as23_sortByNameViaHttp() {
        MvcTestResult result = get("/instruments?name=gas&sort=NAME");

        assertThat(result).hasStatusOk();
        assertThat(jsonList(result, "$.instruments[*].ticker"))
                .containsExactly("CGPA2", "DGCU2", "GBAN", "METR", "TGNO4", "TGSU2");
    }

    @Test
    @DisplayName("AS-24 · Orden inválido")
    void as24_invalidSort() {
        MvcTestResult result = get("/instruments?sort=PRICE");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(jsonString(result, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo("sort");
        assertThat(body(result)).doesNotContain("PRICE");
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private static void assertValidationError(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(result, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo(field);
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
