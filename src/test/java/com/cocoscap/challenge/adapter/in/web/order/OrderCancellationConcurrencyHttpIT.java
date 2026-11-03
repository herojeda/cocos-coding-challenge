package com.cocoscap.challenge.adapter.in.web.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountForUpdateLoader;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.support.AccountProjectionReconciler;
import com.cocoscap.challenge.support.ConcurrentRequests;
import com.cocoscap.challenge.support.TradingTestUsers;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
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
        AccountProjectionReconciler reconciler =
                new AccountProjectionReconciler(transactionManager, accountProjectionPort, accountLedgerPort);

        try {
            warmReady(user.userId());
            List<MvcTestResult> results = ConcurrentRequests.runTogether(
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
            reconciler.assertReconciled(users, user);
        } finally {
            users.deleteUserAndAccount(user);
        }
    }

    private void warmReady(long userId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> accountForUpdateLoader.load(UserId.of(userId)).orElseThrow());
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
