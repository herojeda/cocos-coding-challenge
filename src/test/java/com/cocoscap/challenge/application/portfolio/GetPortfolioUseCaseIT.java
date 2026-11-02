package com.cocoscap.challenge.application.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.AccountState;
import com.cocoscap.challenge.domain.account.AccountState.PositionState;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.portfolio.Portfolio;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.util.Comparator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class GetPortfolioUseCaseIT {

    private static final UserId USER_1 = UserId.of(1L);

    @Autowired
    private GetPortfolioUseCase getPortfolioUseCase;

    @Autowired
    private AccountLedgerPort accountLedgerPort;

    @Autowired
    private CostBasisCalculator costBasisCalculator;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("PF-02 · Usuario sin movimientos")
    void pf02() {
        long userId = insertUser("portfolio-empty@test.com");
        insertAccount(userId, "30010", "0.00", "0.00", "PENDING");

        Portfolio portfolio = getPortfolioUseCase.execute(UserId.of(userId));

        assertThat(portfolio.totalValue()).isEqualByComparingTo("0");
        assertThat(portfolio.availableCash()).isEqualByComparingTo("0");
        assertThat(portfolio.positions()).isEmpty();
    }

    @Test
    @DisplayName("READY projection matches PENDING in-memory rebuild for seed user 1")
    void readyProjectionMatchesPendingRebuild() {
        Portfolio pending = getPortfolioUseCase.execute(USER_1);

        AccountId accountId = AccountId.of(accountIdForUser(USER_1.value()));
        Account rebuilt;
        try (var entries = accountLedgerPort.streamByUserId(USER_1)) {
            rebuilt = Account.rebuildFrom(accountId, entries, costBasisCalculator);
        }
        persistReadyProjection(rebuilt.state());

        Portfolio ready = getPortfolioUseCase.execute(USER_1);

        assertThat(ready)
                .usingRecursiveComparison()
                .withComparatorForType(Comparator.nullsFirst(BigDecimal::compareTo), BigDecimal.class)
                .isEqualTo(pending);
    }

    @Test
    @DisplayName("Portfolio query does not persist a PENDING account")
    void queryDoesNotWritePendingAccount() {
        long accountId = accountIdForUser(USER_1.value());

        getPortfolioUseCase.execute(USER_1);

        String status = jdbcClient
                .sql("SELECT projection_status FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(String.class)
                .single();
        Integer positions = jdbcClient
                .sql("SELECT COUNT(*) FROM positions WHERE account_id = :id")
                .param("id", accountId)
                .query(Integer.class)
                .single();

        assertThat(status).isEqualTo("PENDING");
        assertThat(positions).isZero();
    }

    private void persistReadyProjection(AccountState state) {
        jdbcClient
                .sql(
                        """
                        UPDATE accounts
                        SET cash = :cash,
                            reserved_cash = :reservedCash,
                            projection_status = 'READY'
                        WHERE id = :id
                        """)
                .param("id", state.id().value())
                .param("cash", state.cash())
                .param("reservedCash", state.reservedCash())
                .update();

        for (PositionState position : state.positions()) {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO positions (
                                account_id, instrument_id, quantity, reserved_quantity,
                                average_cost, realized_pnl
                            ) VALUES (
                                :accountId, :instrumentId, :quantity, :reservedQuantity,
                                :averageCost, :realizedPnl
                            )
                            """)
                    .param("accountId", state.id().value())
                    .param("instrumentId", position.instrumentId().value())
                    .param("quantity", position.quantity())
                    .param("reservedQuantity", position.reservedQuantity())
                    .param("averageCost", position.averageCost().orElse(null))
                    .param("realizedPnl", position.realizedPnl())
                    .update();
        }
    }

    private long accountIdForUser(long userId) {
        return jdbcClient
                .sql("SELECT id FROM accounts WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private long insertUser(String email) {
        return jdbcClient
                .sql("INSERT INTO users (email) VALUES (:email) RETURNING id")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private long insertAccount(
            long userId, String accountNumber, String cash, String reservedCash, String status) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO accounts (
                            account_number, user_id, cash, reserved_cash, projection_status
                        ) VALUES (
                            :accountNumber, :userId, :cash, :reservedCash, :status
                        )
                        RETURNING id
                        """)
                .param("accountNumber", accountNumber)
                .param("userId", userId)
                .param("cash", new BigDecimal(cash))
                .param("reservedCash", new BigDecimal(reservedCash))
                .param("status", status)
                .query(Long.class)
                .single();
    }
}
