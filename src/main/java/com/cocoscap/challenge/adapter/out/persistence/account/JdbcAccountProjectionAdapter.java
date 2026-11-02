package com.cocoscap.challenge.adapter.out.persistence.account;

import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.AccountState;
import com.cocoscap.challenge.domain.account.AccountState.PositionState;
import com.cocoscap.challenge.domain.costbasis.CostBasisState;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.user.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Reads and writes {@code accounts} / {@code positions}. Reads join positions only when the
 * projection is {@code READY}. Writes persist the full {@link AccountState} and mark the account
 * {@code READY}.
 */
public class JdbcAccountProjectionAdapter implements AccountProjectionPort {

    private final JdbcClient jdbcClient;

    public JdbcAccountProjectionAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<StoredAccount> findByUserId(UserId userId) {
        List<AccountProjectionRow> rows = jdbcClient
                .sql(
                        """
                        SELECT a.id, a.cash, a.reserved_cash, a.projection_status,
                               p.instrument_id, p.quantity, p.reserved_quantity,
                               p.average_cost, p.realized_pnl
                        FROM accounts a
                        LEFT JOIN positions p
                          ON p.account_id = a.id AND a.projection_status = 'READY'
                        WHERE a.user_id = :userId
                        ORDER BY p.instrument_id
                        """)
                .param("userId", userId.value())
                .query(AccountProjectionRow.class)
                .list();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        // accounts.user_id is UNIQUE (V4, S4): every row belongs to the same account, so the account
        // columns are taken from the first row and the remaining columns are its positions.
        if (rows.stream().map(AccountProjectionRow::id).distinct().count() > 1) {
            throw new IllegalStateException("more than one account for user " + userId.value());
        }

        AccountProjectionRow first = rows.getFirst();
        AccountId accountId = AccountId.of(first.id());
        return switch (first.projectionStatus()) {
            case "PENDING" -> Optional.of(new StoredAccount.Pending(accountId));
            case "READY" -> Optional.of(new StoredAccount.Ready(toState(accountId, rows)));
            default -> throw new IllegalStateException(
                    "unknown projection_status: " + first.projectionStatus());
        };
    }

    @Override
    public void save(AccountState state) {
        requireActiveTransaction("account projection save");
        Objects.requireNonNull(state, "state");

        int updated = jdbcClient
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
        if (updated != 1) {
            throw new IllegalStateException(
                    "expected to update exactly one account row, updated " + updated);
        }

        deletePositionsNotInState(state);
        upsertPositions(state);
    }

    private static AccountState toState(AccountId accountId, List<AccountProjectionRow> rows) {
        AccountProjectionRow first = rows.getFirst();
        List<PositionState> positions = new ArrayList<>();
        for (AccountProjectionRow row : rows) {
            if (row.instrumentId() == null) {
                continue;
            }
            positions.add(toPositionState(row));
        }
        return new AccountState(accountId, first.cash(), first.reservedCash(), List.copyOf(positions));
    }

    private static PositionState toPositionState(AccountProjectionRow row) {
        Optional<BigDecimal> averageCost = Optional.ofNullable(row.averageCost())
                .map(cost -> cost.setScale(CostBasisState.AVERAGE_COST_SCALE, RoundingMode.UNNECESSARY));
        BigDecimal realizedPnl =
                row.realizedPnl().setScale(CostBasisState.REALIZED_PNL_SCALE, RoundingMode.UNNECESSARY);
        return new PositionState(
                InstrumentId.of(row.instrumentId()),
                row.quantity(),
                row.reservedQuantity(),
                averageCost,
                realizedPnl);
    }

    private void deletePositionsNotInState(AccountState state) {
        List<Long> instrumentIds = state.positions().stream()
                .map(position -> position.instrumentId().value())
                .toList();
        if (instrumentIds.isEmpty()) {
            jdbcClient
                    .sql("DELETE FROM positions WHERE account_id = :accountId")
                    .param("accountId", state.id().value())
                    .update();
            return;
        }
        jdbcClient
                .sql(
                        """
                        DELETE FROM positions
                        WHERE account_id = :accountId
                          AND instrument_id NOT IN (:instrumentIds)
                        """)
                .param("accountId", state.id().value())
                .param("instrumentIds", instrumentIds)
                .update();
    }

    private void upsertPositions(AccountState state) {
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
                            ON CONFLICT (account_id, instrument_id) DO UPDATE SET
                                quantity = EXCLUDED.quantity,
                                reserved_quantity = EXCLUDED.reserved_quantity,
                                average_cost = EXCLUDED.average_cost,
                                realized_pnl = EXCLUDED.realized_pnl
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

    private static void requireActiveTransaction(String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(operation + " requires an active transaction");
        }
    }

    record AccountProjectionRow(
            long id,
            BigDecimal cash,
            BigDecimal reservedCash,
            String projectionStatus,
            Long instrumentId,
            Long quantity,
            Long reservedQuantity,
            BigDecimal averageCost,
            BigDecimal realizedPnl) {}
}
