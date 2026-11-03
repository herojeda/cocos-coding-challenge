package com.cocoscap.challenge.application.order;

import com.cocoscap.challenge.domain.order.IdempotencyKey;
import com.cocoscap.challenge.domain.order.Order;
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
     * is unused for that user. Rehydrates the persisted row through {@code Order}'s canonical
     * constructor (any status the ledger can hold).
     */
    Optional<StoredOrder> findByUserAndKey(UserId userId, IdempotencyKey idempotencyKey);

    /**
     * Finds the order with {@code orderId} belonging to {@code userId}. Empty when missing or owned by
     * another user (S11).
     */
    Optional<Order> findByUserAndId(UserId userId, OrderId orderId);

    /**
     * Marks a {@code NEW} order as {@code CANCELLED}. Fails unless exactly one row is updated.
     */
    void markCancelled(OrderId orderId);
}
