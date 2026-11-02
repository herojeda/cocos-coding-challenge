package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
class OrderPlacementHttpIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AccountProjectionPort accountProjectionPort;

    @Autowired
    private AccountLedgerPort accountLedgerPort;

    private TradingTestUsers users;

    @BeforeEach
    void setUp() {
        users = new TradingTestUsers(jdbcClient);
    }

    @Test
    @DisplayName("ORD-06 · Orden sobre los pesos")
    void ord06CurrencyInstrumentIsValidationError() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long arsId = users.instrumentId("ARS");

        MvcTestResult result = place(
                user.userId(),
                "ord-06",
                """
                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":10,"amount":null,"price":null}
                """
                        .formatted(arsId));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(result, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo("instrument_id");
        assertThat(users.countOrders(user.userId())).isZero();
    }

    @Test
    @DisplayName("ORD-07 · Usuario o instrumento inexistente")
    void ord07MissingInstrumentAndUser() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));

        MvcTestResult missingInstrument = place(
                user.userId(),
                "ord-07-i",
                """
                {"instrument_id":999999,"side":"BUY","type":"MARKET","size":10,"amount":null,"price":null}
                """);
        assertThat(missingInstrument).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jsonString(missingInstrument, "$.code")).isEqualTo("INSTRUMENT_NOT_FOUND");
        assertThat(body(missingInstrument)).doesNotContain("999999");
        assertThat(users.countOrders(user.userId())).isZero();

        MvcTestResult missingUser = place(
                9_999_999L,
                "ord-07-u",
                """
                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":10,"amount":null,"price":null}
                """
                        .formatted(users.instrumentId("GGAL")));
        assertThat(missingUser).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jsonString(missingUser, "$.code")).isEqualTo("USER_NOT_FOUND");
        assertThat(body(missingUser)).doesNotContain("9999999");
    }

    @Test
    @DisplayName("ORD-27 · Respuesta de una orden aceptada")
    void ord27AcceptedLimitResponse() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        MvcTestResult result = place(
                user.userId(),
                "ord-27",
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                """
                        .formatted(ggalId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(jsonLong(result, "$.id")).isPositive();
        assertThat(jsonString(result, "$.status")).isEqualTo("NEW");
        assertThat(jsonLong(result, "$.size")).isEqualTo(10L);
        assertThat(jsonDecimal(result, "$.price")).isEqualByComparingTo("850");
        assertThat(jsonString(result, "$.datetime")).endsWith("Z");
        assertThat(result.getResponse().getHeader("Location")).isNull();
        assertThat(users.countOrders(user.userId())).isEqualTo(1);
        assertReconciled(user);
    }

    @Test
    @DisplayName("ORD-33 · Clave de idempotencia ausente o inválida")
    void ord33MissingOrInvalidIdempotencyKey() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        String body =
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                """
                        .formatted(ggalId);

        MvcTestResult missing = mvc.post()
                .uri("/users/{userId}/orders", user.userId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertValidationField(missing, "Idempotency-Key");
        assertThat(users.countOrders(user.userId())).isZero();

        MvcTestResult tooLong = place(user.userId(), "x".repeat(65), body);
        assertValidationField(tooLong, "Idempotency-Key");

        MvcTestResult withSpace = place(user.userId(), "bad key", body);
        assertValidationField(withSpace, "Idempotency-Key");
        assertThat(users.countOrders(user.userId())).isZero();
    }

    @Test
    @DisplayName("ORD-12 · Compra MARKET con efectivo suficiente")
    void ord12FilledMarketBuy() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        MvcTestResult result = place(
                user.userId(),
                "ord-12",
                """
                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":10,"amount":null,"price":null}
                """
                        .formatted(ggalId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(jsonString(result, "$.status")).isEqualTo("FILLED");
        assertThat(jsonLong(result, "$.size")).isEqualTo(10L);
        assertThat(jsonDecimal(result, "$.price")).isEqualByComparingTo("885.80");
        assertThat(users.availableCash(user.userId())).isEqualByComparingTo("91142");
        assertReconciled(user);
    }

    @Test
    @DisplayName("ORD-17 · Compra MARKET sin pesos suficientes")
    void ord17RejectedInsufficientCash() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        MvcTestResult result = place(
                user.userId(),
                "ord-17",
                """
                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":200,"amount":null,"price":null}
                """
                        .formatted(ggalId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(jsonString(result, "$.status")).isEqualTo("REJECTED");
        assertThat(jsonString(result, "$.rejection_reason")).isEqualTo("INSUFFICIENT_CASH");
        assertThat(users.availableCash(user.userId())).isEqualByComparingTo("100000");
        assertReconciled(user);
    }

    @Test
    @DisplayName("ORD-41 · Compra MARKET por monto sin cotización")
    void ord41RejectedAmountWithoutQuote() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long pgrId = users.instrumentId("PGR");

        MvcTestResult result = place(
                user.userId(),
                "ord-41",
                """
                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":null,"amount":10000,"price":null}
                """
                        .formatted(pgrId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(jsonString(result, "$.status")).isEqualTo("REJECTED");
        assertThat(jsonString(result, "$.rejection_reason")).isEqualTo("NO_MARKET_PRICE");
        assertThat(jsonNull(result, "$.size")).isNull();
        assertThat(jsonNull(result, "$.price")).isNull();
        assertThat(jsonDecimal(result, "$.amount")).isEqualByComparingTo("10000");
        assertThat(body(result)).contains("\"size\":null");
        assertThat(body(result)).contains("\"price\":null");
        assertReconciled(user);
    }

    @Test
    @DisplayName("ORD-05 · Lado de transferencia desconocido en el body")
    void ord05UnknownSideIsValidationError() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        MvcTestResult result = place(
                user.userId(),
                "ord-05",
                """
                {"instrument_id":%d,"side":"CASH_OUT","type":"MARKET","size":10,"amount":null,"price":null}
                """
                        .formatted(ggalId));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(result, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(jsonString(result, "$.errors[0].field")).isEqualTo("side");
        assertThat(body(result)).doesNotContain("CASH_OUT");
        assertThat(users.countOrders(user.userId())).isZero();
    }

    private void assertReconciled(TestUser user) {
        StoredAccount stored = accountProjectionPort.findByUserId(users.userId(user)).orElseThrow();
        assertThat(stored).isInstanceOf(StoredAccount.Ready.class);
        Account fromProjection = Account.restore(((StoredAccount.Ready) stored).state(),
                new com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator());
        Account fromLedger;
        try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(users.userId(user))) {
            fromLedger = users.rebuildFromLedger(user, entries);
        }
        assertThat(fromProjection.state()).isEqualTo(fromLedger.state());
    }

    private MvcTestResult place(long userId, String idempotencyKey, String json) {
        return mvc.post()
                .uri("/users/{userId}/orders", userId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static void assertValidationField(MvcTestResult result, String field) {
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

    private static Object jsonNull(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static BigDecimal jsonDecimal(MvcTestResult result, String path) {
        return new BigDecimal(JsonPath.read(body(result), path).toString());
    }

    private static long jsonLong(MvcTestResult result, String path) {
        return ((Number) JsonPath.read(body(result), path)).longValue();
    }
}
