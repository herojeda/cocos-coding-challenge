package com.cocoscap.challenge.domain.order;

/**
 * Raised when an {@link IdempotencyKey} is reused with a different request fingerprint (ADR-008 /
 * ORD-36). The message never includes the key value.
 */
public final class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("idempotency key reused with a different request");
    }
}
