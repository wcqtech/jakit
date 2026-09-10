package com.github.wcqtech.jakit.dbucket;

import com.github.wcqtech.jakit.dbucket.internal.Values;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Point-in-time view of a bucket as stored in the database.
 *
 * <p>{@link #tokens()} is the <em>effective</em> token count: the persisted column plus the tokens
 * that lazily accrued since {@link #lastRefill()}, capped at {@link #capacity()}. It is computed by
 * the database without writing the row, so reading is lock free.
 *
 * <p>{@link #storedTokens()} is the raw, possibly lagging column value and {@link #lastRefill()} the
 * raw timestamp of the last write. Both are exposed for diagnostics only; business code should read
 * {@link #tokens()}.
 *
 * @param namespace    bucket namespace
 * @param name         bucket name
 * @param capacity     burst ceiling
 * @param rate         refill speed in tokens per second
 * @param tokens       effective token count (accrual included, capped at capacity)
 * @param storedTokens persisted token column value, may lag behind {@link #tokens()}
 * @param lastRefill   database timestamp of the last write, may lag behind "now"
 */
public record BucketSnapshot(String namespace,
                             String name,
                             BigDecimal capacity,
                             BigDecimal rate,
                             BigDecimal tokens,
                             BigDecimal storedTokens,
                             Instant lastRefill) {

    public BucketSnapshot {
        namespace = Values.requireText("namespace", namespace, BucketSpec.NAMESPACE_MAX_LENGTH);
        name = Values.requireText("name", name, BucketSpec.NAME_MAX_LENGTH);
        capacity = Values.requireDecimal("capacity", capacity, false);
        rate = Values.requireDecimal("rate", rate, true);
        tokens = Values.requireDecimal("tokens", tokens, true);
        storedTokens = Values.requireDecimal("storedTokens", storedTokens, true);
        if (lastRefill == null) {
            throw new IllegalArgumentException("lastRefill must not be null");
        }
    }

    /**
     * Whether the bucket could satisfy a request for {@code amount} whole tokens right now.
     */
    public boolean canConsume(long amount) {
        return tokens.compareTo(BigDecimal.valueOf(amount)) >= 0;
    }
}
