package com.cocoscap.challenge.application.account;

import com.cocoscap.challenge.domain.account.AccountId;
import com.cocoscap.challenge.domain.account.AccountState;
import java.util.Objects;

/**
 * Result of reading the stored account projection. {@link Ready} carries the materialized state;
 * {@link Pending} means cash and positions on disk are not trustworthy and must not be read.
 */
public sealed interface StoredAccount {

    record Ready(AccountState state) implements StoredAccount {
        public Ready {
            Objects.requireNonNull(state, "state");
        }
    }

    record Pending(AccountId id) implements StoredAccount {
        public Pending {
            Objects.requireNonNull(id, "id");
        }
    }
}
