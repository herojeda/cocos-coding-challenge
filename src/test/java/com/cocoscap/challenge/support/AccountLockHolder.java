package com.cocoscap.challenge.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.application.account.AccountProjectionPort;
import com.cocoscap.challenge.domain.user.UserId;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Holds a real account row lock in another transaction while an action runs (ACCOUNT_BUSY tests).
 */
public final class AccountLockHolder {

    private final PlatformTransactionManager transactionManager;
    private final AccountProjectionPort accountProjectionPort;

    public AccountLockHolder(
            PlatformTransactionManager transactionManager, AccountProjectionPort accountProjectionPort) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.accountProjectionPort = Objects.requireNonNull(accountProjectionPort, "accountProjectionPort");
    }

    public void holdWhile(UserId userId, Runnable action) throws Exception {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(action, "action");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch holderMayFinish = new CountDownLatch(1);

        Thread holder = new Thread(() -> tx.executeWithoutResult(status -> {
            accountProjectionPort.lockByUserId(userId);
            holderHasLock.countDown();
            ConcurrentRequests.await(holderMayFinish);
        }));

        try {
            holder.start();
            assertThat(holderHasLock.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                action.run();
            } finally {
                holderMayFinish.countDown();
            }
            holder.join(10_000);
        } finally {
            holderMayFinish.countDown();
            holder.join(5_000);
        }
    }
}
