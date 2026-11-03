package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.support.AccountLockHolder;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("ORD-32 · La cuenta queda ocupada más allá del tiempo máximo")
    void ord32AccountBusy() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        UserId lockedUser = UserId.of(user.userId());
        long ggalId = users.instrumentId("GGAL");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        double busyBefore = counter(meterRegistry, "trading.account.busy", "operation", "place");
        long lockWaitCountBefore = timerCount(meterRegistry, "trading.account.lock.wait");
        double lockWaitTotalBefore = timerTotalMillis(meterRegistry, "trading.account.lock.wait");
        AccountLockHolder lockHolder = new AccountLockHolder(transactionManager, accountProjectionPort);

        AtomicReference<MvcTestResult> contenderResult = new AtomicReference<>();

        try {
            lockHolder.holdWhile(lockedUser, () -> contenderResult.set(mvc.post()
                    .uri("/users/{userId}/orders", user.userId())
                    .header("Idempotency-Key", "ord-32")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            """
                            {"instrument_id":%d,"side":"BUY","type":"MARKET","size":10,"amount":null,"price":null}
                            """
                                    .formatted(ggalId))
                    .exchange()));

            MvcTestResult result = contenderResult.get();
            assertThat(result).isNotNull();
            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(jsonString(result, "$.code")).isEqualTo("ACCOUNT_BUSY");
            assertThat(users.countOrders(user.userId())).isZero();
            assertThat(counter(meterRegistry, "trading.account.busy", "operation", "place"))
                    .isEqualTo(busyBefore + 1);
            assertThat(timerCount(meterRegistry, "trading.account.lock.wait"))
                    .isEqualTo(lockWaitCountBefore + 1);
            assertThat(timerTotalMillis(meterRegistry, "trading.account.lock.wait") - lockWaitTotalBefore)
                    .isGreaterThanOrEqualTo(200.0);
            Account available = tx.execute(status -> {
                try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(lockedUser)) {
                    return users.rebuildFromLedger(user, entries);
                }
            });
            assertThat(available.availableCash()).isEqualByComparingTo("100000");
        } finally {
            users.deleteUserAndAccount(user);
        }
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String jsonString(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static double counter(MeterRegistry registry, String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static long timerCount(MeterRegistry registry, String name) {
        Timer timer = registry.find(name).timer();
        return timer == null ? 0L : timer.count();
    }

    private static double timerTotalMillis(MeterRegistry registry, String name) {
        Timer timer = registry.find(name).timer();
        return timer == null ? 0.0 : timer.totalTime(TimeUnit.MILLISECONDS);
    }
}
