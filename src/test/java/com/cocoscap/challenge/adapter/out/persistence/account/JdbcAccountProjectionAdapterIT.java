package com.cocoscap.challenge.adapter.out.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.AccountState;
import com.cocoscap.challenge.domain.account.AccountState.PositionState;
import com.cocoscap.challenge.domain.costbasis.CostBasisState;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
class JdbcAccountProjectionAdapterIT {

    @Autowired
    private AccountProjectionPort accountProjectionPort;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("Seed user 1 projection is PENDING")
    void seedUser1ProjectionIsPending() {
        Optional<StoredAccount> stored = accountProjectionPort.findByUserId(UserId.of(1L));

        assertThat(stored).contains(new StoredAccount.Pending(AccountId.of(accountIdForUser(1L))));
    }

    @Test
    @DisplayName("READY projection returns AccountState and restores")
    void readyProjectionReturnsStateAndRestores() {
        long userId = insertUser("projection-ready@test.com");
        long accountId = insertAccount(userId, "20001", "1000.00", "100.00", "READY");
        long pampId = instrumentId("PAMP");
        long bmaId = instrumentId("BMA");

        insertPosition(accountId, pampId, 40L, 5L, new BigDecimal("930.000000"), new BigDecimal("100.00"));
        insertPosition(accountId, bmaId, -10L, 0L, null, new BigDecimal("0.00"));

        StoredAccount stored = accountProjectionPort.findByUserId(UserId.of(userId)).orElseThrow();

        AccountState expected = new AccountState(
                AccountId.of(accountId),
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                List.of(
                        new PositionState(
                                InstrumentId.of(bmaId),
                                -10L,
                                0L,
                                Optional.empty(),
                                BigDecimal.ZERO.setScale(CostBasisState.REALIZED_PNL_SCALE)),
                        new PositionState(
                                InstrumentId.of(pampId),
                                40L,
                                5L,
                                Optional.of(new BigDecimal("930.000000")),
                                new BigDecimal("100.00"))));

        assertThat(stored).isEqualTo(new StoredAccount.Ready(expected));
        assertThatCode(() -> Account.restore(expected, new WeightedAverageCostBasisCalculator()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Unknown user returns empty projection")
    void unknownUserReturnsEmpty() {
        assertThat(accountProjectionPort.findByUserId(UserId.of(9_999_999L))).isEmpty();
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

    private void insertPosition(
            long accountId,
            long instrumentId,
            long quantity,
            long reservedQuantity,
            BigDecimal averageCost,
            BigDecimal realizedPnl) {
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
                .param("accountId", accountId)
                .param("instrumentId", instrumentId)
                .param("quantity", quantity)
                .param("reservedQuantity", reservedQuantity)
                .param("averageCost", averageCost)
                .param("realizedPnl", realizedPnl)
                .update();
    }

    private long instrumentId(String ticker) {
        return jdbcClient
                .sql("SELECT id FROM instruments WHERE ticker = :ticker")
                .param("ticker", ticker)
                .query(Long.class)
                .single();
    }
}
