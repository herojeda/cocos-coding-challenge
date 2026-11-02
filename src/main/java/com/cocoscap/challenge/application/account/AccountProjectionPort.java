package com.cocoscap.challenge.application.account;

import com.cocoscap.challenge.domain.user.UserId;
import java.util.Optional;

/** Reads the materialized account projection ({@code accounts} / {@code positions}). */
public interface AccountProjectionPort {

    /** Empty when the user has no account row. */
    Optional<StoredAccount> findByUserId(UserId userId);
}
