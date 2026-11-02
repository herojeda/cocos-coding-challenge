package com.cocoscap.challenge.application.account;

import com.cocoscap.challenge.domain.user.UserId;
import java.util.Optional;

/**
 * Reads the materialized account projection ({@code accounts} / {@code positions}).
 *
 * <p>A successful read returns cash and positions from a single database snapshot (one statement),
 * so callers do not need {@code REPEATABLE READ} for this consistency alone.
 */
public interface AccountProjectionPort {

    /** Empty when the user has no account row. */
    Optional<StoredAccount> findByUserId(UserId userId);
}
