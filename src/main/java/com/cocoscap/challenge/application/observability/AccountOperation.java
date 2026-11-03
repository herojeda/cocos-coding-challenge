package com.cocoscap.challenge.application.observability;

/** Closed set of account-lock operations for {@code trading.account.busy} (ADR-009). */
public enum AccountOperation {
    PLACE,
    CANCEL
}
