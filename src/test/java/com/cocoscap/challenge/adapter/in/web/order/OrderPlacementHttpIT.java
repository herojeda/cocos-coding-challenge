package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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

    @Autowired
    private MeterRegistry meterRegistry;

    private TradingTestUsers users;

    @BeforeEach
    void setUp() {
        users = new TradingTestUsers(jdbcClient);
    }

    @Test
    @DisplayName("ORD-27 · Respuesta de una orden registrada")
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
    @DisplayName("ORD-12 · Compra MARKET")
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
        double rejectedBefore = counter(
                meterRegistry,
                "trading.orders",
                "type",
                "MARKET",
                "side",
                "BUY",
                "status",
                "REJECTED",
                "rejection_reason",
                "INSUFFICIENT_CASH");

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
        assertThat(counter(
                        meterRegistry,
                        "trading.orders",
                        "type",
                        "MARKET",
                        "side",
                        "BUY",
                        "status",
                        "REJECTED",
                        "rejection_reason",
                        "INSUFFICIENT_CASH"))
                .isEqualTo(rejectedBefore + 1);
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

    private void assertReconciled(TestUser user) {
        StoredAccount stored = accountProjectionPort.findByUserId(users.userId(user)).orElseThrow();
        assertThat(stored).isInstanceOf(StoredAccount.Ready.class);
        Account fromProjection = Account.restore(
                ((StoredAccount.Ready) stored).state(), new WeightedAverageCostBasisCalculator());
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

    private static double counter(MeterRegistry registry, String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
