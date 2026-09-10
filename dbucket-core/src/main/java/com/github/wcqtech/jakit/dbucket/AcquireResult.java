package com.github.wcqtech.jakit.dbucket;

import com.github.wcqtech.jakit.dbucket.internal.Values;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * Outcome of a single acquire attempt.
 *
 * <p>{@link #waited()} is the wall time the call spent trying, which is non-trivial for the blocking
 * acquire and useful for metrics. {@link #remaining()} is only present when the caller asked for it
 * and the dialect could return it (see {@link ConsumeResult}).
 *
 * <p>{@link #degraded()} marks a fail-open decision: the storage failed and the policy allowed the
 * call through instead of rejecting traffic. It is only ever {@code true} together with
 * {@link Outcome#SUCCESS}, so callers that just check {@link #isSuccess()} keep working, while
 * operators can alert on degraded acquires.
 *
 * @param outcome   result kind, never {@code null}
 * @param remaining exact tokens left after a successful acquire, or {@code null}
 * @param waited    time spent in this call, never {@code null}
 * @param degraded  whether a storage failure was absorbed by fail-open
 */
public record AcquireResult(Outcome outcome, BigDecimal remaining, Duration waited, boolean degraded) {

    /** Result kinds of an acquire attempt. */
    public enum Outcome {

        /** Tokens were acquired (or fail-open let the call through). */
        SUCCESS,

        /** The bucket exists but could not satisfy the request in time. */
        INSUFFICIENT,

        /** The bucket does not exist (auto-create disabled or the bucket was deleted). */
        NOT_FOUND
    }

    public AcquireResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (waited == null) {
            throw new IllegalArgumentException("waited must not be null");
        }
        if (remaining != null) {
            remaining = Values.requireDecimal("remaining", remaining, true);
            if (outcome != Outcome.SUCCESS) {
                throw new IllegalArgumentException("remaining is only available on SUCCESS");
            }
        }
        if (degraded && outcome != Outcome.SUCCESS) {
            throw new IllegalArgumentException("degraded is only meaningful on SUCCESS");
        }
    }

    /** Successful acquire with the exact remaining balance. */
    public static AcquireResult success(BigDecimal remaining, Duration waited) {
        return new AcquireResult(Outcome.SUCCESS, remaining, waited, false);
    }

    /** Successful acquire without a known remaining balance. */
    public static AcquireResult success(Duration waited) {
        return new AcquireResult(Outcome.SUCCESS, null, waited, false);
    }

    /** Fail-open success: the storage failed and the policy let the call through. */
    public static AcquireResult degraded(Duration waited) {
        return new AcquireResult(Outcome.SUCCESS, null, waited, true);
    }

    /** Not enough tokens within the allowed time. */
    public static AcquireResult insufficient(Duration waited) {
        return new AcquireResult(Outcome.INSUFFICIENT, null, waited, false);
    }

    /** No such bucket. */
    public static AcquireResult notFound(Duration waited) {
        return new AcquireResult(Outcome.NOT_FOUND, null, waited, false);
    }

    /** Whether tokens were acquired, including a fail-open decision. */
    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    /** Whether an exact remaining balance is attached. */
    public boolean hasRemaining() {
        return remaining != null;
    }
}
