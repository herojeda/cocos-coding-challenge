package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountForUpdateLoader;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
class OrderCancellationHttpIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AccountProjectionPort accountProjectionPort;

    @Autowired
    private AccountLedgerPort accountLedgerPort;

    @Autowired
    private AccountForUpdateLoader accountForUpdateLoader;

    private TradingTestUsers users;

    @BeforeEach
    void setUp() {
        users = new TradingTestUsers(jdbcClient);
    }

    @Test
    @DisplayName("CXL-01 · Cancelación de una compra límite")
    void cxl01CancelBuyLimit() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        long orderId = placeNewLimitBuy(user.userId(), ggalId, 10, "850", "cxl-01");
        assertThat(users.availableCash(user.userId())).isEqualByComparingTo("91500");

        MvcTestResult result = cancel(user.userId(), orderId);

        assertThat(result).hasStatusOk();
        assertThat(jsonString(result, "$.status")).isEqualTo("CANCELLED");
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(users.availableCash(user.userId())).isEqualByComparingTo("100000");
        assertReconciled(user);
    }

    @Test
    @DisplayName("CXL-02 · Cancelación de una venta límite")
    void cxl02CancelSellLimit() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        users.addFilledBuy(user, "PAMP", 40L, new BigDecimal("900.00"), Instant.parse("2023-07-12T13:00:00Z"));
        long pampId = users.instrumentId("PAMP");
        long orderId = jsonLong(
                place(
                        user.userId(),
                        "cxl-02",
                        """
                        {"instrument_id":%d,"side":"SELL","type":"LIMIT","size":15,"amount":null,"price":950}
                        """
                                .formatted(pampId)),
                "$.id");
        assertThat(restoredAccount(user).availableQuantity(InstrumentId.of(pampId))).isEqualTo(25L);

        MvcTestResult result = cancel(user.userId(), orderId);

        assertThat(result).hasStatusOk();
        assertThat(jsonString(result, "$.status")).isEqualTo("CANCELLED");
        assertThat(restoredAccount(user).availableQuantity(InstrumentId.of(pampId))).isEqualTo(40L);
        assertReconciled(user);
    }

    @Test
    @DisplayName("CXL-03 · Cancelación de una orden de la seed")
    void cxl03CancelSeedOrder() {
        long orderId = seedLomaLimitOrderId();
        // Seed accounts start PENDING (cash 0); materialize so available matches the ledger.
        accountForUpdateLoader.load(UserId.of(1L)).orElseThrow();
        assertThat(users.availableCash(1L)).isEqualByComparingTo("627500");

        MvcTestResult result = cancel(1L, orderId);

        assertThat(result).hasStatusOk();
        assertThat(jsonString(result, "$.status")).isEqualTo("CANCELLED");
        assertThat(users.availableCash(1L)).isEqualByComparingTo("663000");
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("CXL-04 · Orden ejecutada")
    void cxl04FilledNotCancellable() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        long orderId = jsonLong(
                place(
                        user.userId(),
                        "cxl-04",
                        """
                        {"instrument_id":%d,"side":"BUY","type":"MARKET","size":10,"amount":null,"price":null}
                        """
                                .formatted(ggalId)),
                "$.id");
        assertThat(orderStatus(orderId)).isEqualTo("FILLED");

        MvcTestResult result = cancel(user.userId(), orderId);

        assertNotCancellable(result, "FILLED", orderId);
    }

    @Test
    @DisplayName("CXL-05 · Orden rechazada")
    void cxl05RejectedNotCancellable() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long pampId = users.instrumentId("PAMP");
        long orderId = jsonLong(
                place(
                        user.userId(),
                        "cxl-05",
                        """
                        {"instrument_id":%d,"side":"SELL","type":"MARKET","size":50,"amount":null,"price":null}
                        """
                                .formatted(pampId)),
                "$.id");
        assertThat(orderStatus(orderId)).isEqualTo("REJECTED");

        MvcTestResult result = cancel(user.userId(), orderId);

        assertNotCancellable(result, "REJECTED", orderId);
    }

    @Test
    @DisplayName("CXL-06 · Orden ya cancelada")
    void cxl06AlreadyCancelled() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        long orderId = placeNewLimitBuy(user.userId(), ggalId, 10, "850", "cxl-06");
        assertThat(cancel(user.userId(), orderId)).hasStatusOk();

        MvcTestResult result = cancel(user.userId(), orderId);

        assertNotCancellable(result, "CANCELLED", orderId);
    }

    @Test
    @DisplayName("CXL-07 · Orden de otro usuario")
    void cxl07OtherUsersOrder() {
        long orderId = seedLomaLimitOrderId();

        MvcTestResult result = cancel(2L, orderId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jsonString(result, "$.code")).isEqualTo("ORDER_NOT_FOUND");
        // Same check as assertNotCancellable: no order id field. A raw digit search is flaky for
        // small seed ids that can appear in status/instance strings.
        assertThat(body(result)).doesNotContain("\"id\":" + orderId);
        assertThat(orderStatus(orderId)).isEqualTo("NEW");
    }

    @Test
    @DisplayName("CXL-08 · Orden inexistente")
    void cxl08MissingOrder() {
        TestUser user = users.createWithCash(new BigDecimal("100000"));

        MvcTestResult result = cancel(user.userId(), 9_999_999L);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jsonString(result, "$.code")).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("Non-positive orderId is a validation error like an invalid userId")
    void nonPositiveOrderIdIsValidationError() {
        MvcTestResult invalidOrder = cancel(1L, 0L);
        MvcTestResult invalidUser = cancel(0L, 1L);

        assertThat(invalidOrder).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(invalidOrder, "$.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(invalidUser).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jsonString(invalidUser, "$.code")).isEqualTo("VALIDATION_ERROR");
    }

    private long seedLomaLimitOrderId() {
        return jdbcClient
                .sql(
                        """
                        SELECT id FROM orders
                        WHERE user_id = 1 AND side = 'BUY' AND type = 'LIMIT' AND status = 'NEW'
                          AND size = 50 AND price = 710
                          AND instrument_id = (SELECT id FROM instruments WHERE ticker = 'LOMA')
                        """)
                .query(Long.class)
                .single();
    }

    private long placeNewLimitBuy(long userId, long instrumentId, long size, String price, String key) {
        return jsonLong(
                place(
                        userId,
                        key,
                        """
                        {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":%d,"amount":null,"price":%s}
                        """
                                .formatted(instrumentId, size, price)),
                "$.id");
    }

    private void assertNotCancellable(MvcTestResult result, String currentStatus, long orderId) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(jsonString(result, "$.code")).isEqualTo("ORDER_NOT_CANCELLABLE");
        assertThat(jsonString(result, "$.current_status")).isEqualTo(currentStatus);
        assertThat(body(result)).doesNotContain("\"id\":" + orderId);
        assertThat(orderStatus(orderId)).isEqualTo(currentStatus);
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

    private Account restoredAccount(TestUser user) {
        StoredAccount stored = accountProjectionPort.findByUserId(users.userId(user)).orElseThrow();
        return Account.restore(
                ((StoredAccount.Ready) stored).state(), new WeightedAverageCostBasisCalculator());
    }

    private String orderStatus(long orderId) {
        return jdbcClient
                .sql("SELECT status FROM orders WHERE id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
    }

    private MvcTestResult place(long userId, String idempotencyKey, String json) {
        return mvc.post()
                .uri("/users/{userId}/orders", userId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult cancel(long userId, long orderId) {
        return mvc.post().uri("/users/{userId}/orders/{orderId}/cancel", userId, orderId).exchange();
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
