package com.cocoscap.challenge.application.portfolio;

import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.application.instrument.InstrumentLookupPort;
import com.cocoscap.challenge.application.market.MarketDataPort;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.AccountState.PositionState;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.costbasis.CostBasisCalculator;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.market.Quote;
import com.cocoscap.challenge.domain.portfolio.Portfolio;
import com.cocoscap.challenge.domain.portfolio.PortfolioValuation;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.domain.user.UserNotFoundException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a user's portfolio. Uses {@code REPEATABLE READ} so account rows and quotes come from one
 * snapshot. Never locks or writes: a {@code PENDING} account is rebuilt in memory only (ADR-005).
 */
public class GetPortfolioUseCase {

    private final AccountProjectionPort accountProjectionPort;
    private final AccountLedgerPort accountLedgerPort;
    private final InstrumentLookupPort instrumentLookupPort;
    private final MarketDataPort marketDataPort;
    private final CostBasisCalculator costBasisCalculator;
    private final PortfolioValuation portfolioValuation;

    public GetPortfolioUseCase(
            AccountProjectionPort accountProjectionPort,
            AccountLedgerPort accountLedgerPort,
            InstrumentLookupPort instrumentLookupPort,
            MarketDataPort marketDataPort,
            CostBasisCalculator costBasisCalculator,
            PortfolioValuation portfolioValuation) {
        this.accountProjectionPort = Objects.requireNonNull(accountProjectionPort, "accountProjectionPort");
        this.accountLedgerPort = Objects.requireNonNull(accountLedgerPort, "accountLedgerPort");
        this.instrumentLookupPort = Objects.requireNonNull(instrumentLookupPort, "instrumentLookupPort");
        this.marketDataPort = Objects.requireNonNull(marketDataPort, "marketDataPort");
        this.costBasisCalculator = Objects.requireNonNull(costBasisCalculator, "costBasisCalculator");
        this.portfolioValuation = Objects.requireNonNull(portfolioValuation, "portfolioValuation");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Portfolio execute(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        StoredAccount stored = accountProjectionPort
                .findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Account account = switch (stored) {
            case StoredAccount.Ready(var state) -> Account.restore(state, costBasisCalculator);
            case StoredAccount.Pending(var accountId) -> {
                try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(userId)) {
                    yield Account.rebuildFrom(accountId, entries, costBasisCalculator);
                }
            }
        };

        Set<InstrumentId> heldIds = account.state().positions().stream()
                .filter(position -> position.quantity() != 0L)
                .map(PositionState::instrumentId)
                .collect(Collectors.toSet());

        Map<InstrumentId, Instrument> instruments = instrumentLookupPort.findByIds(heldIds);
        Map<InstrumentId, Quote> quotes = marketDataPort.findLatestByInstrumentIds(heldIds);
        return portfolioValuation.value(account, instruments, quotes);
    }
}
