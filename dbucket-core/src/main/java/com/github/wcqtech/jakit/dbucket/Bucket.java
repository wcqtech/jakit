package com.github.wcqtech.jakit.dbucket;

import java.time.Duration;
import java.util.Optional;

/**
 * Runtime handle of a single bucket.
 *
 * <p>Handles are cheap values resolved by {@link Dbucket#bucket(String)}; they hold no connection and
 * are safe to keep and share. Every call is a fresh atomic database operation.
 */
public interface Bucket {

    /**
     * Consumes {@code tokens} if they are available right now.
     *
     * <p>When the bucket is missing and auto-creation is enabled, the bucket is created once with the
     * configured {@link DbucketOptions.BucketSpecFactory} and the acquire is retried. Storage failures
     * either propagate as {@link DbucketStoreException} or, with fail-open enabled, produce a degraded
     * success.
     *
     * @param tokens whole tokens to consume, strictly positive
     * @return the attempt outcome, never {@code null}
     */
    AcquireResult tryAcquire(long tokens);

    /**
     * Consumes {@code tokens}, polling until they become available or {@code timeout} elapses.
     *
     * <p>The timeout is a total budget measured from the moment this method is entered; polling does
     * not reset it. Polling uses the configured interval with optional jitter and never holds a
     * connection or a row lock while sleeping. An interrupted thread keeps its interrupt flag and
     * returns {@code false}.
     *
     * <p>Equivalent to {@code acquireResult(tokens, timeout).isSuccess()}.
     *
     * @param tokens  whole tokens to consume, strictly positive
     * @param timeout total wait budget; zero or negative means a single attempt
     * @return {@code true} when the tokens were acquired
     */
    boolean acquire(long tokens, Duration timeout);

    /**
     * Same as {@link #acquire(long, Duration)} but reporting <em>why</em> a wait ended, with
     * {@link AcquireResult#waited()} holding the total time spent (not just the last attempt).
     *
     * <p>On timeout or interruption the result is {@link AcquireResult.Outcome#INSUFFICIENT}; a bucket
     * deleted while waiting yields {@link AcquireResult.Outcome#NOT_FOUND} immediately.
     *
     * @param tokens  whole tokens to consume, strictly positive
     * @param timeout total wait budget; zero or negative means a single attempt
     * @return the acquire outcome, never {@code null}
     */
    AcquireResult acquireResult(long tokens, Duration timeout);

    /** Current state of the bucket, or empty when it does not exist. Reads never fail-open. */
    Optional<BucketSnapshot> snapshot();

    /**
     * Manually deposits whole tokens, capped at capacity.
     *
     * @return {@code false} when the bucket does not exist; storage failures propagate
     */
    boolean deposit(long tokens);

    /** Namespace of this bucket. */
    String namespace();

    /** Name of this bucket. */
    String name();
}
