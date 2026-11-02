package com.cocoscap.challenge.domain.order;

/** Closed catalog of why a registered order was rejected (S13). */
public enum RejectionReason {
    INSUFFICIENT_CASH,
    INSUFFICIENT_HOLDINGS,
    NO_MARKET_PRICE
}
