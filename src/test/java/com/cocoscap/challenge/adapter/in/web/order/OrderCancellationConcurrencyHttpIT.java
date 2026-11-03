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
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
class OrderCancellationConcurrencyHttpIT {

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
    @DisplayName("CXL-09 · Dos cancelaciones de la misma orden")
    void cxl09ConcurrentCancels() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        long orderId = jsonLong(
                place(
                        user.userId(),
                        "cxl-09",
                        """
                        {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                        """
                                .formatted(ggalId)),
                "$.id");

        try {
            warmReady(user.userId());
            List<MvcTestResult> results = runConcurrent(
                    () -> cancel(user.userId(), orderId), () -> cancel(user.userId(), orderId));

            List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).toList();
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);

            MvcTestResult ok = results.stream()
                    .filter(r -> r.getResponse().getStatus() == 200)
                    .findFirst()
                    .orElseThrow();
            assertThat(jsonString(ok, "$.status")).isEqualTo("CANCELLED");

            MvcTestResult conflict = results.stream()
                    .filter(r -> r.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(jsonString(conflict, "$.code")).isEqualTo("ORDER_NOT_CANCELLABLE");
            assertThat(jsonString(conflict, "$.current_status")).isEqualTo("CANCELLED");

            // Reservation released once: 100000, not 100000 + 8500.
            assertThat(users.availableCash(user.userId())).isEqualByComparingTo("100000");
            assertReconciled(users, user);
        } finally {
            users.deleteUserAndAccount(user);
        }
    }

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

    private MvcTestResult cancel(long userId, long orderId) {
        return mvc.post().uri("/users/{userId}/orders/{orderId}/cancel", userId, orderId).exchange();
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
