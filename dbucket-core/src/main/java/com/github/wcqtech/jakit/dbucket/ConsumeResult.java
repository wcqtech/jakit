package com.github.wcqtech.jakit.dbucket;

import com.github.wcqtech.jakit.dbucket.internal.Values;
import java.math.BigDecimal;

/**
 * Outcome of a single {@link BucketStore#tryConsume(String, String, long, boolean)} attempt.
 *
 * <p>{@link #remaining()} is the exact token count left in the bucket after a successful consume.
 * It is present only when the caller asked for it <em>and</em> the storage can return it without an
 * extra locking read (PostgreSQL style {@code RETURNING}; on MySQL the store may use a short
 * transaction or omit it). It is therefore {@code null} in the default configuration and always
 * {@code null} for non-success outcomes.
 *
 * @param outcome   result kind, never {@code null}
 * @param remaining exact tokens left after a successful consume, or {@code null}
 */
public record ConsumeResult(Outcome outcome, BigDecimal remaining) {

    /** Result kinds of a consume attempt. */
    public enum Outcome {

        /** The tokens were consumed atomically. */
        SUCCESS,

        /** The bucket exists but holds fewer tokens than requested. */
        INSUFFICIENT,

        /** The bucket does not exist (auto-create disabled, or the bucket was deleted). */
        NOT_FOUND
    }

    public ConsumeResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (remaining != null) {
            remaining = Values.requireDecimal("remaining", remaining, true);
            if (outcome != Outcome.SUCCESS) {
                throw new IllegalArgumentException("remaining is only available on SUCCESS");
            }
        }
    }

    /** Successful consume without a known remaining count. */
    public static ConsumeResult success() {
        return new ConsumeResult(Outcome.SUCCESS, null);
    }

    /** Successful consume with the exact remaining count. */
    public static ConsumeResult success(BigDecimal remaining) {
        return new ConsumeResult(Outcome.SUCCESS, remaining);
    }

    /** Not enough tokens. */
    public static ConsumeResult insufficient() {
        return new ConsumeResult(Outcome.INSUFFICIENT, null);
    }

    /** No such bucket. */
    public static ConsumeResult notFound() {
        return new ConsumeResult(Outcome.NOT_FOUND, null);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public boolean hasRemaining() {
        return remaining != null;
    }
}
