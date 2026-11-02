package com.cocoscap.challenge.application.order;

import com.cocoscap.challenge.domain.order.IdempotencyKey;
import com.cocoscap.challenge.domain.order.OrderDecision;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.order.RequestHash;
import com.cocoscap.challenge.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

/** Persistence for the order book: insert placement outcomes and look up by idempotency key. */
public interface OrderBookPort {

    /**
     * Inserts a placement decision with its idempotency key and fingerprint, returning the generated
     * id ({@code INSERT … RETURNING id}).
     */
    OrderId register(
            OrderDecision decision, IdempotencyKey idempotencyKey, RequestHash requestHash, Instant datetime);

    /**
     * Finds the order registered for {@code userId} under {@code idempotencyKey}. Empty when the key
     * is unused for that user. Supports {@code NEW}, {@code FILLED} and {@code REJECTED}; {@code
     * CANCELLED} is out of scope until cancellation.
     */
    Optional<StoredOrder> findByUserAndKey(UserId userId, IdempotencyKey idempotencyKey);
}
