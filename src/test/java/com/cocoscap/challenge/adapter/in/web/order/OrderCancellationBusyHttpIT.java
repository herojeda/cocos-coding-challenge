package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.support.AccountLockHolder;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@TestPropertySource(properties = "cocos.persistence.account-lock-timeout=200ms")
class OrderCancellationBusyHttpIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private AccountProjectionPort accountProjectionPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("CXL-10 · La cuenta queda ocupada más allá del tiempo máximo")
    void cxl10AccountBusy() throws Exception {
        TradingTestUsers users = new TradingTestUsers(jdbcClient);
        TestUser user = users.createWithCash(new BigDecimal("100000"));
        long ggalId = users.instrumentId("GGAL");
        long orderId = jsonLong(
                place(
                        user.userId(),
                        "cxl-10",
                        """
                        {"instrument_id":%d,"side":"BUY","type":"LIMIT","size":10,"amount":null,"price":850}
                        """
                                .formatted(ggalId)),
                "$.id");
        UserId lockedUser = UserId.of(user.userId());
        double busyBefore = counter(meterRegistry, "trading.account.busy", "operation", "cancel");
        AccountLockHolder lockHolder = new AccountLockHolder(transactionManager, accountProjectionPort);

        AtomicReference<MvcTestResult> contenderResult = new AtomicReference<>();

        try {
            lockHolder.holdWhile(lockedUser, () -> contenderResult.set(cancel(user.userId(), orderId)));

            MvcTestResult result = contenderResult.get();
            assertThat(result).isNotNull();
            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(jsonString(result, "$.code")).isEqualTo("ACCOUNT_BUSY");
            assertThat(orderStatus(orderId)).isEqualTo("NEW");
            assertThat(counter(meterRegistry, "trading.account.busy", "operation", "cancel"))
                    .isEqualTo(busyBefore + 1);
        } finally {
            users.deleteUserAndAccount(user);
        }
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

    private String orderStatus(long orderId) {
        return jdbcClient
                .sql("SELECT status FROM orders WHERE id = :id")
                .param("id", orderId)
                .query(String.class)
                .single();
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

    private static double counter(MeterRegistry registry, String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
