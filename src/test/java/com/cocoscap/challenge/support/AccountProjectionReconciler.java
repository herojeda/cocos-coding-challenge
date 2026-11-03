package com.cocoscap.challenge.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.application.account.AccountLedgerPort;
import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.application.account.StoredAccount;
import com.cocoscap.challenge.domain.account.Account;
import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.costbasis.WeightedAverageCostBasisCalculator;
import com.cocoscap.challenge.domain.user.UserId;
import com.cocoscap.challenge.support.TradingTestUsers.TestUser;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Asserts the account projection matches a rebuild from the ledger (ADR-006). */
public final class AccountProjectionReconciler {

    private final PlatformTransactionManager transactionManager;
    private final AccountProjectionPort accountProjectionPort;
    private final AccountLedgerPort accountLedgerPort;

    public AccountProjectionReconciler(
            PlatformTransactionManager transactionManager,
            AccountProjectionPort accountProjectionPort,
            AccountLedgerPort accountLedgerPort) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.accountProjectionPort = Objects.requireNonNull(accountProjectionPort, "accountProjectionPort");
        this.accountLedgerPort = Objects.requireNonNull(accountLedgerPort, "accountLedgerPort");
    }

    public void assertReconciled(TradingTestUsers users, TestUser user) {
        Objects.requireNonNull(users, "users");
        Objects.requireNonNull(user, "user");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            StoredAccount stored =
                    accountProjectionPort.findByUserId(UserId.of(user.userId())).orElseThrow();
            assertThat(stored).isInstanceOf(StoredAccount.Ready.class);
            Account fromProjection = Account.restore(
                    ((StoredAccount.Ready) stored).state(), new WeightedAverageCostBasisCalculator());
            Account fromLedger;
            try (Stream<LedgerEntry> entries = accountLedgerPort.streamByUserId(UserId.of(user.userId()))) {
                fromLedger = users.rebuildFromLedger(user, entries);
            }
            assertThat(fromProjection.state()).isEqualTo(fromLedger.state());
        });
    }
}
