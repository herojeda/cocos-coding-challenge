package com.cocoscap.challenge.domain.user;

/** Identity of a user. */
public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new IllegalArgumentException("UserId must be positive");
        }
    }

    public static UserId of(long value) {
        return new UserId(value);
    }
}
