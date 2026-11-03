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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class OrderPlacementConcurrencyHttpIT {

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("ORD-28 · Dos compras simultáneas que superan el disponible")
    void ord28ConcurrentBuys() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("10000"));
        long ggalId = users.instrumentId("GGAL");
        String body = marketBuy(ggalId, 10);

        try {
            warmReady(user.userId());
            List<MvcTestResult> results = runConcurrent(
                    () -> place(user.userId(), "ord-28-a", body),
                    () -> place(user.userId(), "ord-28-b", body));

            List<String> statuses = results.stream().map(r -> jsonString(r, "$.status")).toList();
            assertThat(statuses).containsExactlyInAnyOrder("FILLED", "REJECTED");
            MvcTestResult rejected = results.stream()
                    .filter(r -> "REJECTED".equals(jsonString(r, "$.status")))
                    .findFirst()
                    .orElseThrow();
            assertThat(jsonString(rejected, "$.rejection_reason")).isEqualTo("INSUFFICIENT_CASH");
            assertThat(users.countOrders(user.userId())).isEqualTo(2);

            Account account = rebuild(users, user);
            assertThat(account.availableCash().signum()).isGreaterThanOrEqualTo(0);
            assertThat(account.availableCash()).isEqualByComparingTo("1142");
            assertReconciled(users, user);
        } finally {
            users.deleteUserAndAccount(user);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("ORD-29 · Dos ventas simultáneas que superan la tenencia")
    void ord29ConcurrentSells() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        users.addFilledBuy(user, "PAMP", 40L, new BigDecimal("900.00"), Instant.parse("2023-07-12T13:00:00Z"));
        long pampId = users.instrumentId("PAMP");
        String body = marketSell(pampId, 30);

        try {
            warmReady(user.userId());
            List<MvcTestResult> results = runConcurrent(
                    () -> place(user.userId(), "ord-29-a", body),
                    () -> place(user.userId(), "ord-29-b", body));

            List<String> statuses = results.stream().map(r -> jsonString(r, "$.status")).toList();
            assertThat(statuses).containsExactlyInAnyOrder("FILLED", "REJECTED");
            MvcTestResult rejected = results.stream()
                    .filter(r -> "REJECTED".equals(jsonString(r, "$.status")))
                    .findFirst()
                    .orElseThrow();
            assertThat(jsonString(rejected, "$.rejection_reason")).isEqualTo("INSUFFICIENT_HOLDINGS");
            assertThat(users.countOrders(user.userId())).isEqualTo(3); // prior BUY + 2 sells

            Account account = rebuild(users, user);
            assertThat(account.availableQuantity(InstrumentId.of(pampId))).isGreaterThanOrEqualTo(0);
            assertThat(account.availableQuantity(InstrumentId.of(pampId))).isEqualTo(10L);
            assertReconciled(users, user);
        } finally {
            users.deleteUserAndAccount(user);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Two concurrent MARKET buys of distinct instruments both fill")
    void concurrentBuysOfDistinctInstrumentsBothFill() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        long pampId = users.instrumentId("PAMP");

        try {
            warmReady(user.userId());
            List<MvcTestResult> results = runConcurrent(
                    () -> place(user.userId(), "distinct-a", marketBuy(ggalId, 10)),
                    () -> place(user.userId(), "distinct-b", marketBuy(pampId, 10)));

            List<String> statuses = results.stream().map(r -> jsonString(r, "$.status")).toList();
            assertThat(statuses).containsExactly("FILLED", "FILLED");
            assertThat(users.countOrders(user.userId())).isEqualTo(2);

            Account account = rebuild(users, user);
            assertThat(account.availableQuantity(InstrumentId.of(ggalId))).isEqualTo(10L);
            assertThat(account.availableQuantity(InstrumentId.of(pampId))).isEqualTo(10L);
            assertReconciled(users, user);
        } finally {
            users.deleteUserAndAccount(user);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("ORD-38 · Dos pedidos simultáneos con la misma clave")
    void ord38ConcurrentSameKey() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        String body = marketBuy(ggalId, 10);

        try {
            List<MvcTestResult> results = runConcurrent(
                    () -> place(user.userId(), "k-5", body),
                    () -> place(user.userId(), "k-5", body));

            assertThat(users.countOrders(user.userId())).isEqualTo(1);
            List<Integer> statuses = results.stream()
                    .map(r -> r.getResponse().getStatus())
                    .toList();
            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED.value(), HttpStatus.OK.value());

            MvcTestResult created = results.stream()
                    .filter(r -> r.getResponse().getStatus() == HttpStatus.CREATED.value())
                    .findFirst()
                    .orElseThrow();
            MvcTestResult replayed = results.stream()
                    .filter(r -> r.getResponse().getStatus() == HttpStatus.OK.value())
                    .findFirst()
                    .orElseThrow();
            assertThat(replayed.getResponse().getHeader(OrderController.IDEMPOTENT_REPLAYED_HEADER))
                    .isEqualTo("true");
            assertThat(jsonLong(created, "$.id")).isEqualTo(jsonLong(replayed, "$.id"));
            assertReconciled(users, user);
        } finally {
            users.deleteUserAndAccount(user);
        }
    }

    /** Rebuilds a PENDING fixture to READY before racing, without changing scenario data. */
    private void warmReady(long userId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> accountForUpdateLoader.load(UserId.of(userId)).orElseThrow());
    }

    private List<MvcTestResult> runConcurrent(RequestCall first, RequestCall second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<MvcTestResult> firstResult = new AtomicReference<>();
        AtomicReference<MvcTestResult> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            ready.countDown();
            await(start);
            try {
                firstResult.set(first.call());
            } catch (Throwable ex) {
                firstError.set(ex);
            }
        });
        Thread t2 = new Thread(() -> {
            ready.countDown();
            await(start);
            try {
                secondResult.set(second.call());
            } catch (Throwable ex) {
                secondError.set(ex);
            }
        });

        t1.start();
        t2.start();
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        t1.join(15_000);
        t2.join(15_000);

        assertThat(firstError.get()).isNull();
        assertThat(secondError.get()).isNull();
        List<MvcTestResult> results = new ArrayList<>();
        results.add(firstResult.get());
        results.add(secondResult.get());
        assertThat(results).doesNotContainNull();
        return results;
    }

    private Account rebuild(TradingTestUsers users, TestUser user) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(UserId.of(user.userId()))) {
                return users.rebuildFromLedger(user, entries);
            }
        });
    }

    private void assertReconciled(TradingTestUsers users, TestUser user) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            StoredAccount stored =
                    accountProjectionPort.findByUserId(UserId.of(user.userId())).orElseThrow();
            assertThat(stored).isInstanceOf(StoredAccount.Ready.class);
            Account fromProjection = Account.restore(
                    ((StoredAccount.Ready) stored).state(), new WeightedAverageCostBasisCalculator());
            Account fromLedger;
            try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(UserId.of(user.userId()))) {
                fromLedger = users.rebuildFromLedger(user, entries);
            }
            assertThat(fromProjection.state()).isEqualTo(fromLedger.state());
        });
    }

    private MvcTestResult place(long userId, String idempotencyKey, String json) {
        return mvc.post()
                .uri("/users/{userId}/orders", userId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static String marketBuy(long instrumentId, long size) {
        return """
                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":%d,"amount":null,"price":null}
                """
                .formatted(instrumentId, size);
    }

    private static String marketSell(long instrumentId, long size) {
        return """
                {"instrument_id":%d,"side":"SELL","type":"MARKET","size":%d,"amount":null,"price":null}
                """
                .formatted(instrumentId, size);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
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

    @FunctionalInterface
    private interface RequestCall {
        MvcTestResult call();
    }
}
