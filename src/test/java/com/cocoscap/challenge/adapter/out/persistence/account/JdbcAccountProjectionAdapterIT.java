package com.cocoscap.challenge.adapter.out.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountBusyException;
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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class JdbcAccountProjectionAdapterIT {

    private static final CostBasisCalculator COST_BASIS = new WeightedAverageCostBasisCalculator();

    @Autowired
    private AccountProjectionPort accountProjectionPort;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    @DisplayName("lock times out with AccountBusyException while another transaction holds it")
    void lockTimesOutWithAccountBusyException() throws Exception {
        long userId = insertUserCommitted("projection-lock-busy@test.com");
        long accountId = insertAccountCommitted(userId, "20011", "10.00", "0.00", "PENDING");
        UserId lockedUser = UserId.of(userId);
        AccountProjectionPort shortTimeoutPort =
                new JdbcAccountProjectionAdapter(jdbcClient, Duration.ofMillis(200));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch holderMayFinish = new CountDownLatch(1);
        AtomicReference<Throwable> contenderError = new AtomicReference<>();

        Thread holder = new Thread(() -> tx.executeWithoutResult(status -> {
            accountProjectionPort.lockByUserId(lockedUser);
            holderHasLock.countDown();
            await(holderMayFinish);
        }));
        Thread contender = new Thread(() -> {
            await(holderHasLock);
            try {
                tx.executeWithoutResult(status -> shortTimeoutPort.lockByUserId(lockedUser));
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
            contender.join(5_000);
            holder.join(5_000);

            assertThat(contenderError.get())
                    .isInstanceOf(AccountBusyException.class)
                    .hasMessage("account lock timed out")
                    .hasMessageNotContaining(String.valueOf(userId))
                    .hasMessageNotContaining(String.valueOf(accountId));
        } finally {
            holderMayFinish.countDown();
            holder.join(5_000);
            contender.join(5_000);
            deleteAccountAndUser(accountId, userId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("lock waiter reads the cash and positions committed by the previous holder")
    void lockWaiterReadsCommittedState() throws Exception {
        long userId = insertUserCommitted("projection-lock-wait@test.com");
        long accountId = insertAccountCommitted(userId, "20012", "10.00", "0.00", "READY");
        long pampId = instrumentId("PAMP");
        long ggalId = instrumentId("GGAL");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> insertPosition(
                accountId, pampId, 40L, 0L, new BigDecimal("900.000000"), new BigDecimal("0.00")));
        UserId lockedUser = UserId.of(userId);
        // The holder changes an existing position and adds a new one. Under READ COMMITTED, a
        // single SELECT ... JOIN positions ... FOR UPDATE OF a would re-read only the account row
        // after waiting, and the waiter would see PAMP 40 and no GGAL.
        AccountState updated = new AccountState(
                AccountId.of(accountId),
                new BigDecimal("77.00"),
                new BigDecimal("3.00"),
                List.of(
                        new PositionState(
                                InstrumentId.of(ggalId),
                                5L,
                                0L,
                                Optional.of(new BigDecimal("885.800000")),
                                new BigDecimal("0.00")),
                        new PositionState(
                                InstrumentId.of(pampId),
                                10L,
                                0L,
                                Optional.of(new BigDecimal("900.000000")),
                                new BigDecimal("150.00"))));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch holderMayFinish = new CountDownLatch(1);
        AtomicReference<StoredAccount> seenByWaiter = new AtomicReference<>();
        AtomicReference<Throwable> waiterError = new AtomicReference<>();

        Thread holder = new Thread(() -> tx.executeWithoutResult(status -> {
            accountProjectionPort.lockByUserId(lockedUser);
            holderHasLock.countDown();
            await(waiterStarted);
            // Give the waiter time to block on FOR UPDATE before we write and commit.
            sleep(150);
            accountProjectionPort.save(updated);
            holderMayFinish.countDown();
        }));
        Thread waiter = new Thread(() -> {
            await(holderHasLock);
            waiterStarted.countDown();
            try {
                tx.executeWithoutResult(status ->
                        seenByWaiter.set(accountProjectionPort.lockByUserId(lockedUser).orElseThrow()));
            } catch (Throwable ex) {
                waiterError.set(ex);
            }
        });

        try {
            holder.start();
            waiter.start();
            assertThat(holderMayFinish.await(5, TimeUnit.SECONDS)).isTrue();
            holder.join(5_000);
            waiter.join(5_000);

            assertThat(waiterError.get()).isNull();
            assertThat(seenByWaiter.get()).isEqualTo(new StoredAccount.Ready(updated));
        } finally {
            holderMayFinish.countDown();
            holder.join(5_000);
            waiter.join(5_000);
            deleteAccountAndUser(accountId, userId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("lockByUserId and save require an active transaction")
    void lockAndSaveRequireActiveTransaction() {
        AccountState anyState = new AccountState(
                AccountId.of(accountIdForUser(1L)),
                new BigDecimal("1.00"),
                new BigDecimal("0.00"),
                List.of());

        assertThatThrownBy(() -> accountProjectionPort.lockByUserId(UserId.of(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
        assertThatThrownBy(() -> accountProjectionPort.save(anyState))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    @DisplayName("lockByUserId returns empty when the user has no account")
    void lockByUserIdReturnsEmptyWhenNoAccount() {
        assertThat(accountProjectionPort.lockByUserId(UserId.of(9_999_999L))).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
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

    private long insertUserCommitted(String email) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> insertUser(email));
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

    private long insertAccountCommitted(
            long userId, String accountNumber, String cash, String reservedCash, String status) {
        return new TransactionTemplate(transactionManager)
                .execute(tx -> insertAccount(userId, accountNumber, cash, reservedCash, status));
    }

    private void deleteAccountAndUser(long accountId, long userId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcClient
                    .sql("DELETE FROM positions WHERE account_id = :accountId")
                    .param("accountId", accountId)
                    .update();
            jdbcClient
                    .sql("DELETE FROM accounts WHERE id = :accountId")
                    .param("accountId", accountId)
                    .update();
            jdbcClient
                    .sql("DELETE FROM users WHERE id = :userId")
                    .param("userId", userId)
                    .update();
        });
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
