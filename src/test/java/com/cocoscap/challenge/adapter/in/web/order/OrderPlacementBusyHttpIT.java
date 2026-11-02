package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@TestPropertySource(properties = "cocos.persistence.account-lock-timeout=200ms")
class OrderPlacementBusyHttpIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AccountProjectionPort accountProjectionPort;

    @Autowired
    private AccountLedgerPort accountLedgerPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("ORD-32 · La cuenta queda ocupada más allá del tiempo máximo")
    void ord32AccountBusy() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        UserId lockedUser = UserId.of(user.userId());
        long ggalId = users.instrumentId("GGAL");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch holderMayFinish = new CountDownLatch(1);
        AtomicReference<MvcTestResult> contenderResult = new AtomicReference<>();
        AtomicReference<Throwable> contenderError = new AtomicReference<>();

        Thread holder = new Thread(() -> tx.executeWithoutResult(status -> {
            accountProjectionPort.lockByUserId(lockedUser);
            holderHasLock.countDown();
            await(holderMayFinish);
        }));
        Thread contender = new Thread(() -> {
            await(holderHasLock);
            try {
                contenderResult.set(mvc.post()
                        .uri("/users/{userId}/orders", user.userId())
                        .header("Idempotency-Key", "ord-32")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"instrument_id":%d,"side":"BUY","type":"MARKET","size":10,"amount":null,"price":null}
                                """
                                        .formatted(ggalId))
                        .exchange());
            } catch (Throwable ex) {
                contenderError.set(ex);
            } finally {
                holderMayFinish.countDown();
            }
        });

        try {
            holder.start();
            contender.start();
            assertThat(holderHasLock.await(5, TimeUnit.SECONDS)).isTrue();
            contender.join(10_000);
            holder.join(10_000);

            assertThat(contenderError.get()).isNull();
            MvcTestResult result = contenderResult.get();
            assertThat(result).isNotNull();
            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(jsonString(result, "$.code")).isEqualTo("ACCOUNT_BUSY");
            assertThat(users.countOrders(user.userId())).isZero();
            Account available = tx.execute(status -> {
                try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(lockedUser)) {
                    return users.rebuildFromLedger(user, entries);
                }
            });
            assertThat(available.availableCash()).isEqualByComparingTo("100000");
        } finally {
            holderMayFinish.countDown();
            holder.join(5_000);
            contender.join(5_000);
            users.deleteUserAndAccount(user);
        }
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
}
