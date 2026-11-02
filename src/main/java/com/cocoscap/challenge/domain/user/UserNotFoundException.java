package com.cocoscap.challenge.domain.user;

/** Raised when a requested user (and thus their account) does not exist. */
public final class UserNotFoundException extends RuntimeException {

    private final UserId userId;

    public UserNotFoundException(UserId userId) {
        super("user not found: " + userId.value());
        this.userId = userId;
    }

    public UserId userId() {
        return userId;
    }
}
