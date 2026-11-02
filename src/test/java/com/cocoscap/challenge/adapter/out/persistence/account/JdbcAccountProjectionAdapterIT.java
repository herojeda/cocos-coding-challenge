package com.cocoscap.challenge.adapter.out.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.AccountState;
import com.cocoscap.challenge.domain.account.AccountState.PositionState;
import com.cocoscap.challenge.domain.account.User1SeedLedgerFixture;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class JdbcAccountProjectionAdapterIT {

    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

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
    @DisplayName("READY projection with no positions returns empty holdings")
    void readyProjectionWithNoPositions() {
        long userId = insertUser("projection-ready-empty@test.com");
        long accountId = insertAccount(userId, "20002", "50.00", "0.00", "READY");

        StoredAccount stored = accountProjectionPort.findByUserId(UserId.of(userId)).orElseThrow();

        assertThat(stored)
                .isEqualTo(new StoredAccount.Ready(new AccountState(
                        AccountId.of(accountId),
                        new BigDecimal("50.00"),
                        new BigDecimal("0.00"),
                        List.of())));
    }

    @Test
    @DisplayName("Unknown user returns empty projection")
    void unknownUserReturnsEmpty() {
        assertThat(accountProjectionPort.findByUserId(UserId.of(9_999_999L))).isEmpty();
    }

    @Test
    @DisplayName("save persists rebuilt user-1 state as READY")
    void savePersistsRebuiltUser1StateAsReady() {
        AccountId accountId = AccountId.of(accountIdForUser(1L));
        AccountState state = Account.rebuildFrom(
                        accountId, User1SeedLedgerFixture.entries().stream(), COST_BASIS)
                .state();

        accountProjectionPort.save(state);

        StoredAccount stored = accountProjectionPort.findByUserId(UserId.of(1L)).orElseThrow();
        assertThat(stored).isEqualTo(new StoredAccount.Ready(state));
        assertThat(projectionStatus(accountId.value())).isEqualTo("READY");
    }

    @Test
    @DisplayName("save deletes positions absent from the new state")
    void saveDeletesPositionsAbsentFromState() {
        long userId = insertUser("projection-save-prune@test.com");
        long accountId = insertAccount(userId, "20010", "100.00", "0.00", "READY");
        long pampId = instrumentId("PAMP");
        long metrId = instrumentId("METR");
        insertPosition(accountId, pampId, 10L, 0L, new BigDecimal("900.000000"), new BigDecimal("0.00"));
        insertPosition(accountId, metrId, 5L, 0L, new BigDecimal("200.000000"), new BigDecimal("0.00"));

        AccountState withoutMetr = new AccountState(
                AccountId.of(accountId),
                new BigDecimal("100.00"),
                new BigDecimal("0.00"),
                List.of(new PositionState(
                        InstrumentId.of(pampId),
                        10L,
                        0L,
                        Optional.of(new BigDecimal("900.000000")),
                        new BigDecimal("0.00"))));

        accountProjectionPort.save(withoutMetr);

        assertThat(accountProjectionPort.findByUserId(UserId.of(userId)))
                .contains(new StoredAccount.Ready(withoutMetr));
        assertThat(positionCount(accountId)).isEqualTo(1);
        assertThat(hasPosition(accountId, metrId)).isFalse();
    }

    @Test
    @DisplayName("save upsert updates every column of an existing position")
    void saveUpsertUpdatesEveryPositionColumn() {
        long userId = insertUser("projection-save-upsert@test.com");
        long accountId = insertAccount(userId, "20013", "100.00", "0.00", "READY");
        long pampId = instrumentId("PAMP");
        insertPosition(accountId, pampId, 10L, 0L, new BigDecimal("900.000000"), new BigDecimal("0.00"));

        AccountState updated = new AccountState(
                AccountId.of(accountId),
                new BigDecimal("100.00"),
                new BigDecimal("0.00"),
                List.of(new PositionState(
                        InstrumentId.of(pampId),
                        15L,
                        5L,
                        Optional.of(new BigDecimal("920.000000")),
                        new BigDecimal("50.00"))));

        accountProjectionPort.save(updated);

        assertThat(accountProjectionPort.findByUserId(UserId.of(userId)))
                .contains(new StoredAccount.Ready(updated));
    }

    @Test
    @DisplayName("save upsert clears average_cost when the position has no cost")
    void saveUpsertClearsAverageCostWhenEmpty() {
        long userId = insertUser("projection-save-null-cost@test.com");
        long accountId = insertAccount(userId, "20014", "100.00", "0.00", "READY");
        long pampId = instrumentId("PAMP");
        insertPosition(accountId, pampId, 10L, 0L, new BigDecimal("900.000000"), new BigDecimal("0.00"));

        AccountState closed = new AccountState(
                AccountId.of(accountId),
                new BigDecimal("100.00"),
                new BigDecimal("0.00"),
                List.of(new PositionState(
                        InstrumentId.of(pampId),
                        0L,
                        0L,
                        Optional.empty(),
                        new BigDecimal("50.00"))));

        accountProjectionPort.save(closed);

        assertThat(accountProjectionPort.findByUserId(UserId.of(userId)))
                .contains(new StoredAccount.Ready(closed));
        assertThat(averageCost(accountId, pampId)).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("save requires an active transaction")
    void saveRequiresActiveTransaction() {
        AccountState anyState = new AccountState(
                AccountId.of(accountIdForUser(1L)),
                new BigDecimal("1.00"),
                new BigDecimal("0.00"),
                List.of());

        assertThatThrownBy(() -> accountProjectionPort.save(anyState))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    private String projectionStatus(long accountId) {
        return jdbcClient
                .sql("SELECT projection_status FROM accounts WHERE id = :id")
                .param("id", accountId)
                .query(String.class)
                .single();
    }

    private int positionCount(long accountId) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM positions WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query(Integer.class)
                .single();
    }

    private boolean hasPosition(long accountId, long instrumentId) {
        Integer count = jdbcClient
                .sql(
                        """
                        SELECT COUNT(*) FROM positions
                        WHERE account_id = :accountId AND instrument_id = :instrumentId
                        """)
                .param("accountId", accountId)
                .param("instrumentId", instrumentId)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    private BigDecimal averageCost(long accountId, long instrumentId) {
        return jdbcClient
                .sql(
                        """
                        SELECT average_cost FROM positions
                        WHERE account_id = :accountId AND instrument_id = :instrumentId
                        """)
                .param("accountId", accountId)
                .param("instrumentId", instrumentId)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
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
