package com.cocoscap.challenge.application.account;

import com.cocoscap.challenge.application.observability.TradingMetricsPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.user.UserId;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks the account for a write use case and rebuilds a {@code PENDING} projection under that lock
 * (ADR-005). Requires an existing transaction ({@link Propagation#MANDATORY}). Not {@code final} so
 * Spring can apply the transactional proxy.
 */
public class AccountForUpdateLoader {

    private final AccountProjectionPort accountProjectionPort;
    private final AccountLedgerPort accountLedgerPort;
    private final CostBasisCalculator costBasisCalculator;
    private final TradingMetricsPort tradingMetricsPort;

    public AccountForUpdateLoader(
            AccountProjectionPort accountProjectionPort,
            AccountLedgerPort accountLedgerPort,
            CostBasisCalculator costBasisCalculator,
            TradingMetricsPort tradingMetricsPort) {
        this.accountProjectionPort = Objects.requireNonNull(accountProjectionPort, "accountProjectionPort");
        this.accountLedgerPort = Objects.requireNonNull(accountLedgerPort, "accountLedgerPort");
        this.costBasisCalculator = Objects.requireNonNull(costBasisCalculator, "costBasisCalculator");
        this.tradingMetricsPort = Objects.requireNonNull(tradingMetricsPort, "tradingMetricsPort");
    }

    /** Empty when the user has no account; callers choose the matching error. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Account> load(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        return accountProjectionPort.lockByUserId(userId).map(stored -> switch (stored) {
            case StoredAccount.Ready(var state) -> Account.restore(state, costBasisCalculator);
            case StoredAccount.Pending(var accountId) -> rebuildAndPersist(userId, accountId);
        });
    }

    private Account rebuildAndPersist(UserId userId, AccountId accountId) {
        long started = System.nanoTime();
        try {
            Account account;
            try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(userId)) {
                account = Account.rebuildFrom(accountId, entries, costBasisCalculator);
            }
            accountProjectionPort.save(account.state());
            tradingMetricsPort.projectionRebuilt(elapsed(started), true);
            return account;
        } catch (RuntimeException ex) {
            tradingMetricsPort.projectionRebuilt(elapsed(started), false);
            throw ex;
        }
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
