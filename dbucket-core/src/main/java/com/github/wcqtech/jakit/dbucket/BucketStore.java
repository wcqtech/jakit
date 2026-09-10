package com.github.wcqtech.jakit.dbucket;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Storage SPI for a database-backed distributed token bucket.
 *
 * <p>The interface is deliberately small: it exposes only operations that map onto a
 * <em>single atomic database statement</em>. Waiting, retrying, timeouts, auto-creation, fail-open
 * decisions and metrics belong to the policy layer above it, so custom implementations never have
 * to reimplement them.
 *
 * <h2>Implementation contract</h2>
 * <ul>
 *   <li><b>Thread safety.</b> Implementations must be thread-safe and effectively stateless; one
 *       instance is shared by the whole application.</li>
 *   <li><b>Single atomic statement.</b> Every mutating method must execute as one atomic statement
 *       whose guard is evaluated inside the database (for example
 *       {@code UPDATE ... WHERE effectiveTokens >= ?}). Never emulate a guard with a
 *       read-modify-write sequence in Java.</li>
 *   <li><b>Lazy refill inside the statement.</b> Refill is computed and applied by the database in
 *       the same statement that consumes or deposits tokens, based on the persisted
 *       {@code last_refill} column. Refill must be capped at {@code capacity} and the fractional
 *       remainder must be preserved, never truncated.</li>
 *   <li><b>Database time only.</b> Accrual must use the database clock ({@code NOW()} and friends);
 *       client supplied timestamps are not part of this SPI.</li>
 *   <li><b>Independent connection.</b> Statements must not join a caller managed transaction; each
 *       one runs on its own connection so the row lock lives only as long as the statement.</li>
 *   <li><b>Error semantics.</b> Database failures must be reported as
 *       {@link DbucketStoreException}; errors must not be swallowed. Mutating statements must not be
 *       retried internally: consuming a token is at-most-once.</li>
 *   <li><b>Missing bucket.</b> Methods that address an unknown bucket report it through their
 *       result ({@code false}, {@link ConsumeResult.Outcome#NOT_FOUND} or
 *       {@link Optional#empty()}) instead of throwing.</li>
 * </ul>
 *
 * <h2>Token semantics</h2>
 * Token columns are fixed point decimals with {@value BucketSpec#TOKEN_SCALE} fractional digits
 * ({@code NUMERIC(20,6)} / {@code DECIMAL(20,6)}). Quantities passed in and out of this SPI are
 * whole {@code long} tokens; only the stored balance and refill rate are fractional.
 */
public interface BucketStore {

    /**
     * Ensures a bucket exists, creating it when absent. Must be race safe (unique key plus a
     * dialect specific upsert).
     *
     * <p>When the bucket already exists the stored configuration wins and the request's capacity and
     * rate are ignored; the returned result carries the current snapshot so the caller can detect the
     * mismatch. When the bucket is created, its balance is
     * {@link BucketSpec#initialTokens()}.
     *
     * @param spec bucket definition to create; never {@code null}
     * @return whether the bucket was created or already existed
     * @throws DbucketStoreException if the statement fails
     */
    CreateResult createIfAbsent(BucketSpec spec);

    /**
     * Reads the current bucket state without writing the row.
     *
     * <p>The implementation must compute the effective token count (persisted balance plus accrual
     * since {@code last_refill}, capped at capacity) inside the {@code SELECT}. Reading must not take
     * a write lock, so a hot bucket is not serialized by queries.
     *
     * @return the snapshot, or empty when the bucket does not exist
     * @throws DbucketStoreException if the statement fails
     */
    Optional<BucketSnapshot> get(String namespace, String name);

    /**
     * Atomically accrues and consumes {@code tokens} whole tokens.
     *
     * <p>The guard must be evaluated on the <em>post-refill</em> effective balance, otherwise a bucket
     * that is momentarily empty but already refillable would be rejected incorrectly. A successful
     * call returns {@link ConsumeResult.Outcome#SUCCESS}; the balance can never go negative and never
     * exceeds capacity.
     *
     * @param namespace     bucket namespace
     * @param name          bucket name
     * @param tokens        whole tokens to consume, strictly positive and not greater than capacity
     * @param withRemaining when {@code true}, request the exact remaining balance after the consume.
     *                      Implementations may return {@link ConsumeResult#success()} without a
     *                      remaining value when the dialect cannot produce it cheaply; when they do
     *                      produce it, it must be exact.
     * @return the consume outcome
     * @throws DbucketStoreException if the statement fails
     */
    ConsumeResult tryConsume(String namespace, String name, long tokens, boolean withRemaining);

    /**
     * Manually deposits {@code tokens} whole tokens.
     *
     * <p>The statement accrues first, then adds the deposit and caps the result at capacity
     * (overflow is discarded), and advances {@code last_refill} to the database clock.
     *
     * @param tokens whole tokens to add, strictly positive
     * @return {@code false} when the bucket does not exist, {@code true} otherwise (including when the
     *         deposit was fully discarded because the bucket was already full)
     * @throws DbucketStoreException if the statement fails
     */
    boolean deposit(String namespace, String name, long tokens);

    /**
     * Replaces the bucket capacity.
     *
     * <p>Shrinking must clamp the stored balance atomically ({@code tokens = LEAST(tokens, capacity)});
     * growing has no side effect because lazy refill fills the extra room over time.
     *
     * @param capacity new capacity, strictly positive
     * @return {@code false} when the bucket does not exist
     * @throws DbucketStoreException if the statement fails
     */
    boolean adjustCapacity(String namespace, String name, BigDecimal capacity);

    /**
     * Replaces the refill rate.
     *
     * <p>Only future accrual is affected; the current balance is untouched. The rate may be
     * {@code 0} to disable time based refill.
     *
     * @param rate new refill rate in tokens per second, zero or positive
     * @return {@code false} when the bucket does not exist
     * @throws DbucketStoreException if the statement fails
     */
    boolean adjustRate(String namespace, String name, BigDecimal rate);

    /**
     * Deletes the bucket, releasing its row.
     *
     * <p>Deletion wins over concurrent operations: the row lock serializes both, so a caller either
     * sees the bucket or gets {@link ConsumeResult.Outcome#NOT_FOUND}, never an intermediate state.
     * Blocking waiters observe {@code NOT_FOUND} and stop without compensation.
     *
     * @return {@code false} when the bucket did not exist
     * @throws DbucketStoreException if the statement fails
     */
    boolean delete(String namespace, String name);
}
