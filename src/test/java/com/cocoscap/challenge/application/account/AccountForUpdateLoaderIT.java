package com.cocoscap.challenge.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.user.UserId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, AccountForUpdateLoaderIT.TestDoublesConfig.class})
@Transactional
class AccountForUpdateLoaderIT {

    private static final UserId USER_1 = UserId.of(1L);
    private static final AtomicBoolean FAIL_LEDGER = new AtomicBoolean();

    @Autowired
    private AccountForUpdateLoader accountForUpdateLoader;

    @Autowired
    private AccountProjectionPort accountProjectionPort;

    @Autowired
    private RecordingTradingMetrics tradingMetrics;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetDoubles() {
        FAIL_LEDGER.set(false);
        tradingMetrics.clear();
    }

    @Test
    @DisplayName("ACB-11 · Disponible del usuario de la seed")
    void pendingAccountIsRebuiltAndPersistedAsReady() {
        Account account = accountForUpdateLoader.load(USER_1).orElseThrow();

        assertThat(account.cash()).isEqualByComparingTo("753000");
        assertThat(account.availableCash()).isEqualByComparingTo("627500");
        assertThat(accountProjectionPort.findByUserId(USER_1))
                .contains(new StoredAccount.Ready(account.state()));
        assertThat(tradingMetrics.rebuilds()).containsExactly(true);
    }

    @Test
    @DisplayName("READY path does not rebuild again in the same transaction")
    void readyPathDoesNotRebuildAgain() {
        accountForUpdateLoader.load(USER_1).orElseThrow();
        Account second = accountForUpdateLoader.load(USER_1).orElseThrow();

        assertThat(second.availableCash()).isEqualByComparingTo("627500");
        assertThat(tradingMetrics.rebuilds()).containsExactly(true);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Failed rebuild reports failure, rolls back, and leaves the account PENDING")
    void failedRebuildLeavesAccountPending() {
        FAIL_LEDGER.set(true);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> accountForUpdateLoader.load(USER_1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger boom");

        assertThat(tradingMetrics.rebuilds()).containsExactly(false);
        assertThat(accountProjectionPort.findByUserId(USER_1).orElseThrow())
                .isInstanceOf(StoredAccount.Pending.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("load requires an active transaction")
    void loadRequiresActiveTransaction() {
        assertThatThrownBy(() -> accountForUpdateLoader.load(USER_1))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDoublesConfig {

        @Bean
        @Primary
        AccountLedgerPort controllableAccountLedgerPort(
                @Qualifier("accountLedgerPort") AccountLedgerPort delegate) {
            return userId -> {
                if (FAIL_LEDGER.get()) {
                    throw new IllegalStateException("ledger boom");
                }
                return delegate.streamByUserId(userId);
            };
        }

        @Bean
        @Primary
        RecordingTradingMetrics recordingTradingMetrics() {
            return new RecordingTradingMetrics();
        }
    }

    static final class RecordingTradingMetrics implements TradingMetricsPort {

        private final List<Boolean> rebuilds = new ArrayList<>();

        @Override
        public void projectionRebuilt(Duration duration, boolean success) {
            rebuilds.add(success);
        }

        List<Boolean> rebuilds() {
            return List.copyOf(rebuilds);
        }

        void clear() {
            rebuilds.clear();
        }
    }
}
