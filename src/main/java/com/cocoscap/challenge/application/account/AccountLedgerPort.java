package com.cocoscap.challenge.application.account;

import com.cocoscap.challenge.domain.account.LedgerEntry;
import com.cocoscap.challenge.domain.user.UserId;
import java.util.stream.Stream;

/**
 * Reads a user's order ledger for account reconstruction.
 *
 * <p>Entries are ordered by {@code (datetime, id)}. The call must run inside a transaction: the
 * PostgreSQL driver only uses a server-side cursor when the connection is not in autocommit. The
 * caller owns the stream and must close it (for example with try-with-resources).
 */
public interface AccountLedgerPort {

    Stream<LedgerEntry> streamByUserId(UserId userId);
}
