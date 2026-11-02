package com.cocoscap.challenge.application.account;

import com.cocoscap.challenge.domain.account.AccountState;
import com.cocoscap.challenge.domain.user.UserId;
import java.util.Optional;

/**
 * Reads and writes the materialized account projection ({@code accounts} / {@code positions}).
 *
 * <p>A successful read returns cash and positions from a single database snapshot (one statement),
 * so callers do not need {@code REPEATABLE READ} for this consistency alone.
 */
public interface AccountProjectionPort {

    /** Empty when the user has no account row. */
    Optional<StoredAccount> findByUserId(UserId userId);

    /**
     * Persists the full account state and marks the projection {@code READY}.
     *
     * <p>Requires an active transaction. The caller must hold the account lock
     * ({@code lockByUserId}) in that same transaction: this overwrites the whole projection, so
     * saving an account read without the lock can silently undo a concurrent write (ADR-006).
     * Only write use cases call this.
     */
    void save(AccountState state);
}
