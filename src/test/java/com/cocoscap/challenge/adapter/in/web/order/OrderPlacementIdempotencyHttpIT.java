package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class OrderPlacementIdempotencyHttpIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    private TradingTestUsers users;

    @BeforeEach
    void setUp() {
        users = new TradingTestUsers(jdbcClient);
    }

    @Test
    @DisplayName("ORD-34 · Reintento de una orden ejecutada")
    void ord34ReplayReturnsSameOrder() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        String body = marketBuy(ggalId, 10);

        MvcTestResult first = place(user.userId(), "k-1", body);
        assertThat(first).hasStatus(HttpStatus.CREATED);
        long orderId = jsonLong(first, "$.id");
        assertThat(users.availableCash(user.userId())).isEqualByComparingTo("91142");

        MvcTestResult replay = place(user.userId(), "k-1", body);
        assertThat(replay).hasStatusOk();
        assertThat(replay.getResponse().getHeader(OrderController.IDEMPOTENT_REPLAYED_HEADER)).isEqualTo("true");
        assertThat(jsonLong(replay, "$.id")).isEqualTo(orderId);
        assertThat(users.countOrders(user.userId())).isEqualTo(1);
        assertThat(users.availableCash(user.userId())).isEqualByComparingTo("91142");
    }

    @Test
    @DisplayName("ORD-35 · Reintento de una orden rechazada")
    void ord35ReplayKeepsRejectionAfterCashIn() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        String body = marketBuy(ggalId, 200);

        MvcTestResult first = place(user.userId(), "k-2", body);
        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(jsonString(first, "$.status")).isEqualTo("REJECTED");
        assertThat(jsonString(first, "$.rejection_reason")).isEqualTo("INSUFFICIENT_CASH");
        long orderId = jsonLong(first, "$.id");
        long ordersAfterFirst = users.countOrders(user.userId());

        insertCashInAndMarkPending(user, new BigDecimal("100000"));

        MvcTestResult replay = place(user.userId(), "k-2", body);
        assertThat(replay).hasStatusOk();
        assertThat(replay.getResponse().getHeader(OrderController.IDEMPOTENT_REPLAYED_HEADER)).isEqualTo("true");
        assertThat(jsonLong(replay, "$.id")).isEqualTo(orderId);
        assertThat(jsonString(replay, "$.status")).isEqualTo("REJECTED");
        assertThat(jsonString(replay, "$.rejection_reason")).isEqualTo("INSUFFICIENT_CASH");
        assertThat(users.countOrders(user.userId())).isEqualTo(ordersAfterFirst);
    }

    @Test
    @DisplayName("ORD-36 · Clave reutilizada con otro pedido")
    void ord36KeyReusedWithDifferentRequest() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        assertThat(place(user.userId(), "k-3", marketBuy(ggalId, 10))).hasStatus(HttpStatus.CREATED);
        long ordersAfterFirst = users.countOrders(user.userId());

        MvcTestResult reused = place(user.userId(), "k-3", marketBuy(ggalId, 20));
        assertThat(reused).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(jsonString(reused, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(body(reused)).doesNotContain("k-3");
        assertThat(users.countOrders(user.userId())).isEqualTo(ordersAfterFirst);
    }

    @Test
    @DisplayName("ORD-37 · La misma clave en usuarios distintos")
    void ord37SameKeyOnDifferentUserIsProcessed() {
        TestUser first = users.createWithCash(new BigDecimal("100000"));
        TestUser second = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        assertThat(place(first.userId(), "k-4", marketBuy(ggalId, 10))).hasStatus(HttpStatus.CREATED);
        MvcTestResult otherUser = place(
                second.userId(),
                "k-4",
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                """
                        .formatted(ggalId));
        assertThat(otherUser).hasStatus(HttpStatus.CREATED);
        assertThat(otherUser.getResponse().getHeader(OrderController.IDEMPOTENT_REPLAYED_HEADER)).isNull();
        assertThat(jsonString(otherUser, "$.status")).isEqualTo("NEW");
        assertThat(users.countOrders(second.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("ORD-40 · El reintento muestra el estado actual de la orden")
    void ord40ReplayShowsCancelledStatus() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        String body =
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                """
                        .formatted(ggalId);

        MvcTestResult first = place(user.userId(), "k-7", body);
        assertThat(first).hasStatus(HttpStatus.CREATED);
        long orderId = jsonLong(first, "$.id");

        MvcTestResult cancelled = mvc.post()
                .uri("/users/{userId}/orders/{orderId}/cancel", user.userId(), orderId)
                .exchange();
        assertThat(cancelled).hasStatusOk();
        assertThat(jsonString(cancelled, "$.status")).isEqualTo("CANCELLED");

        MvcTestResult replay = place(user.userId(), "k-7", body);
        assertThat(replay).hasStatusOk();
        assertThat(replay.getResponse().getHeader(OrderController.IDEMPOTENT_REPLAYED_HEADER)).isEqualTo("true");
        assertThat(jsonString(replay, "$.status")).isEqualTo("CANCELLED");
        assertThat(jsonLong(replay, "$.id")).isEqualTo(orderId);
        assertThat(users.countOrders(user.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("ORD-39 · Reintento después de un error de validación")
    void ord39KeyFreeAfterValidationError() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        MvcTestResult invalid = place(
                user.userId(),
                "k-6",
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":null}
                """
                        .formatted(ggalId));
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(invalid, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(users.countOrders(user.userId())).isZero();

        MvcTestResult valid = place(
                user.userId(),
                "k-6",
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                """
                        .formatted(ggalId));
        assertThat(valid).hasStatus(HttpStatus.CREATED);
        assertThat(users.countOrders(user.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Normalized decimals with the same key are a replay")
    void normalizedPriceScalesAreReplay() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");

        MvcTestResult first = place(
                user.userId(),
                "k-norm",
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                """
                        .formatted(ggalId));
        assertThat(first).hasStatus(HttpStatus.CREATED);
        long orderId = jsonLong(first, "$.id");

        MvcTestResult replay = place(
                user.userId(),
                "k-norm",
                """
                {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850.00}
                """
                        .formatted(ggalId));
        assertThat(replay).hasStatusOk();
        assertThat(replay.getResponse().getHeader(OrderController.IDEMPOTENT_REPLAYED_HEADER)).isEqualTo("true");
        assertThat(jsonLong(replay, "$.id")).isEqualTo(orderId);
        assertThat(users.countOrders(user.userId())).isEqualTo(1);
    }

    private void insertCashInAndMarkPending(TestUser user, BigDecimal amount) {
        long arsId = users.instrumentId("ARS");
        jdbcClient
                .sql(
                        """
                        INSERT INTO orders (
                            instrument_id, user_id, size, price, type, side, status, datetime
                        ) VALUES (
                            :instrumentId, :userId, :size, 1.00, 'MARKET', 'CASH_IN', 'FILLED', :datetime
                        )
                        """)
                .param("instrumentId", arsId)
                .param("userId", user.userId())
                .param("size", amount)
                .param("datetime", OffsetDateTime.ofInstant(Instant.parse("2023-07-12T16:00:00Z"), ZoneOffset.UTC))
                .update();
        jdbcClient
                .sql(
                        """
                        UPDATE accounts
                        SET projection_status = 'PENDING', cash = 0, reserved_cash = 0
                        WHERE id = :accountId
                        """)
                .param("accountId", user.accountId())
                .update();
        jdbcClient
                .sql("DELETE FROM positions WHERE account_id = :accountId")
                .param("accountId", user.accountId())
                .update();
    }

    private static String marketBuy(long instrumentId, long size) {
        return """
                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":%d,"amount":null,"price":null}
                """
                .formatted(instrumentId, size);
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

    private static long jsonLong(MvcTestResult result, String path) {
        return ((Number) JsonPath.read(body(result), path)).longValue();
    }
}
