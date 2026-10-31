package com.cocoscap.challenge.domain.account;

/** Identity of an {@link Account}. Equality of accounts is based only on this value. */
public record AccountId(long value) {

    public AccountId {
        if (value <= 0) {
            throw new IllegalArgumentException("AccountId must be positive");
        }
    }

    public static AccountId of(long value) {
        return new AccountId(value);
    }
}
