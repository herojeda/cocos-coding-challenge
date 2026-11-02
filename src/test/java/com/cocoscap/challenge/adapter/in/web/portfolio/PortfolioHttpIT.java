package com.cocoscap.challenge.adapter.in.web.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class PortfolioHttpIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("PF-01 · Portfolio del usuario de la seed")
    void pf01() {
        MvcTestResult result = get("/users/1/portfolio");

        assertThat(result).hasStatusOk();
        assertThat(jsonDecimal(result, "$.available_cash")).isEqualByComparingTo("627500");
        assertThat(jsonDecimal(result, "$.total_value")).isEqualByComparingTo("889756");
        assertThat(jsonList(result, "$.positions[*].ticker")).containsExactly("BMA", "METR", "PAMP");
        assertThat(jsonLong(result, "$.positions[?(@.ticker=='PAMP')].quantity")).isEqualTo(40L);
        assertThat(jsonLong(result, "$.positions[?(@.ticker=='METR')].quantity")).isEqualTo(500L);
        assertThat(jsonLong(result, "$.positions[?(@.ticker=='BMA')].quantity")).isEqualTo(-10L);

        assertThat(jsonDecimal(result, "$.positions[?(@.ticker=='PAMP')].market_value"))
                .isEqualByComparingTo("37034");
        assertThat(jsonDecimal(result, "$.positions[?(@.ticker=='PAMP')].total_return_pct"))
                .isEqualByComparingTo("-0.45");
        assertThat(jsonDecimal(result, "$.positions[?(@.ticker=='PAMP')].average_cost"))
                .isEqualByComparingTo("930");
        assertThat(jsonDecimal(result, "$.positions[?(@.ticker=='PAMP')].realized_pnl"))
                .isEqualByComparingTo("100");
        assertThat(jsonDecimal(result, "$.positions[?(@.ticker=='PAMP')].daily_change_pct"))
                .isEqualByComparingTo("0.44");

        // Negative holding: market value present; cost and return absent.
        assertThat(jsonDecimal(result, "$.positions[?(@.ticker=='BMA')].market_value"))
                .isEqualByComparingTo("-15028");
        assertThat(jsonNull(result, "$.positions[?(@.ticker=='BMA')].average_cost")).isNull();
        assertThat(jsonNull(result, "$.positions[?(@.ticker=='BMA')].total_return_pct")).isNull();
        assertThat(body(result)).contains("\"total_return_pct\"");
        assertThat(body(result)).contains("\"daily_change_pct\"");
        assertThat(body(result)).doesNotContain("\"total_return\":");
        assertThat(body(result)).doesNotContain("\"daily_change\":");
    }

    @Test
    @DisplayName("PF-03 · Usuario inexistente")
    void pf03() {
        MvcTestResult result = get("/users/9999999/portfolio");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(jsonString(result, "$.code")).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("READY projection is valued without rebuilding the ledger")
    void readyProjectionSmoke() {
        long userId = insertUser("portfolio-ready@test.com");
        long accountId = insertAccount(userId, "30001", "100000.00", "0.00", "READY");
        long pampId = instrumentId("PAMP");
        insertPosition(accountId, pampId, 40L, 0L, new BigDecimal("930.000000"), new BigDecimal("0.00"));

        MvcTestResult result = get("/users/" + userId + "/portfolio");

        assertThat(result).hasStatusOk();
        assertThat(jsonDecimal(result, "$.available_cash")).isEqualByComparingTo("100000");
        assertThat(jsonDecimal(result, "$.total_value")).isEqualByComparingTo("137034");
        assertThat(jsonDecimal(result, "$.positions[0].market_value")).isEqualByComparingTo("37034");
    }

    @Test
    @DisplayName("Position without quote exposes null valuation fields")
    void positionWithoutQuoteHasNulls() {
        long userId = insertUser("portfolio-no-quote@test.com");
        long accountId = insertAccount(userId, "30002", "100000.00", "0.00", "READY");
        long pgrId = instrumentId("PGR");
        insertPosition(accountId, pgrId, 100L, 0L, new BigDecimal("10.000000"), new BigDecimal("0.00"));

        MvcTestResult result = get("/users/" + userId + "/portfolio");

        assertThat(result).hasStatusOk();
        assertThat(jsonDecimal(result, "$.total_value")).isEqualByComparingTo("100000");
        assertThat(jsonLong(result, "$.positions[0].quantity")).isEqualTo(100L);
        assertThat(jsonNull(result, "$.positions[0].market_value")).isNull();
        assertThat(jsonNull(result, "$.positions[0].total_return_pct")).isNull();
        assertThat(jsonNull(result, "$.positions[0].daily_change_pct")).isNull();
        assertThat(body(result)).contains("\"market_value\":null");
        assertThat(body(result)).contains("\"total_return_pct\":null");
        assertThat(body(result)).contains("\"daily_change_pct\":null");
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private long insertUser(String email) {
        return jdbcClient
                .sql("INSERT INTO users (email) VALUES (:email) RETURNING id")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private long insertAccount(
            long userId, String accountNumber, String cash, String reservedCash, String status) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO accounts (
                            account_number, user_id, cash, reserved_cash, projection_status
                        ) VALUES (
                            :accountNumber, :userId, :cash, :reservedCash, :status
                        )
                        RETURNING id
                        """)
                .param("accountNumber", accountNumber)
                .param("userId", userId)
                .param("cash", new BigDecimal(cash))
                .param("reservedCash", new BigDecimal(reservedCash))
                .param("status", status)
                .query(Long.class)
                .single();
    }

    private void insertPosition(
            long accountId,
            long instrumentId,
            long quantity,
            long reservedQuantity,
            BigDecimal averageCost,
            BigDecimal realizedPnl) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO positions (
                            account_id, instrument_id, quantity, reserved_quantity,
                            average_cost, realized_pnl
                        ) VALUES (
                            :accountId, :instrumentId, :quantity, :reservedQuantity,
                            :averageCost, :realizedPnl
                        )
                        """)
                .param("accountId", accountId)
                .param("instrumentId", instrumentId)
                .param("quantity", quantity)
                .param("reservedQuantity", reservedQuantity)
                .param("averageCost", averageCost)
                .param("realizedPnl", realizedPnl)
                .update();
    }

    private long instrumentId(String ticker) {
        return jdbcClient
                .sql("SELECT id FROM instruments WHERE ticker = :ticker")
                .param("ticker", ticker)
                .query(Long.class)
                .single();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String jsonString(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static Object jsonNull(MvcTestResult result, String path) {
        return unwrap(JsonPath.read(body(result), path));
    }

    private static BigDecimal jsonDecimal(MvcTestResult result, String path) {
        return new BigDecimal(unwrap(JsonPath.read(body(result), path)).toString());
    }

    private static long jsonLong(MvcTestResult result, String path) {
        return ((Number) unwrap(JsonPath.read(body(result), path))).longValue();
    }

    private static Object unwrap(Object value) {
        if (value instanceof List<?> list) {
            return list.getFirst();
        }
        return value;
    }

    private static List<String> jsonList(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }
}
