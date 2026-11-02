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
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads {@code accounts} by {@code user_id} in one statement, joining {@code positions} only when
 * the projection is {@code READY}, so cash and holdings come from a single database snapshot.
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

        AccountProjectionRow first = rows.getFirst();
        AccountId accountId = AccountId.of(first.id());
        return switch (first.projectionStatus()) {
            case "PENDING" -> Optional.of(new StoredAccount.Pending(accountId));
            case "READY" -> Optional.of(new StoredAccount.Ready(toState(accountId, rows)));
            default -> throw new IllegalStateException(
                    "unknown projection_status: " + first.projectionStatus());
        };
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
        return new AccountState(
                accountId, scaleCash(first.cash()), scaleCash(first.reservedCash()), List.copyOf(positions));
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

    private static BigDecimal scaleCash(BigDecimal value) {
        return value.setScale(AccountState.CASH_SCALE, RoundingMode.UNNECESSARY);
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
