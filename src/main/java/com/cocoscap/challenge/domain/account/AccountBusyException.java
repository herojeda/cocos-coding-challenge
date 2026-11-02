package com.cocoscap.challenge.domain.account;

/**
 * Raised when the pessimistic lock on an account could not be acquired within the configured
 * timeout ({@code lock_timeout}). Callers may retry. The message never names the user or account.
 */
public final class AccountBusyException extends RuntimeException {

    public AccountBusyException() {
        super("account lock timed out");
    }

    public AccountBusyException(Throwable cause) {
        super("account lock timed out", cause);
    }
}
