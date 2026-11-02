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
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads {@code accounts} by {@code user_id}. {@code READY} also loads {@code positions}; {@code
 * PENDING} returns only the account id.
 */
public class JdbcAccountProjectionAdapter implements AccountProjectionPort {

    private final JdbcClient jdbcClient;

    public JdbcAccountProjectionAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<StoredAccount> findByUserId(UserId userId) {
        Optional<AccountRow> account = jdbcClient
                .sql(
                        """
                        SELECT id, cash, reserved_cash, projection_status
                        FROM accounts
                        WHERE user_id = :userId
                        """)
                .param("userId", userId.value())
                .query(AccountRow.class)
                .optional();

        if (account.isEmpty()) {
            return Optional.empty();
        }

        AccountRow row = account.get();
        AccountId accountId = AccountId.of(row.id());
        return switch (row.projectionStatus()) {
            case "PENDING" -> Optional.of(new StoredAccount.Pending(accountId));
            case "READY" -> Optional.of(new StoredAccount.Ready(toState(row, accountId)));
            default -> throw new IllegalStateException(
                    "unknown projection_status: " + row.projectionStatus());
        };
    }

    private AccountState toState(AccountRow row, AccountId accountId) {
        List<PositionState> positions = jdbcClient
                .sql(
                        """
                        SELECT instrument_id, quantity, reserved_quantity, average_cost, realized_pnl
                        FROM positions
                        WHERE account_id = :accountId
                        ORDER BY instrument_id
                        """)
                .param("accountId", accountId.value())
                .query(PositionRow.class)
                .list()
                .stream()
                .map(JdbcAccountProjectionAdapter::toPositionState)
                .toList();

        return new AccountState(
                accountId,
                scaleCash(row.cash()),
                scaleCash(row.reservedCash()),
                positions);
    }

    private static PositionState toPositionState(PositionRow row) {
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

    record AccountRow(long id, BigDecimal cash, BigDecimal reservedCash, String projectionStatus) {}

    record PositionRow(
            long instrumentId,
            long quantity,
            long reservedQuantity,
            BigDecimal averageCost,
            BigDecimal realizedPnl) {}
}
