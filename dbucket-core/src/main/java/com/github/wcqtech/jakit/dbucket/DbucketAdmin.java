package com.github.wcqtech.jakit.dbucket;

import java.math.BigDecimal;

/**
 * Lifecycle operations, separate from the hot acquire path.
 *
 * <p>Administrative calls never auto-create and never fail-open: a storage failure propagates as
 * {@link DbucketStoreException} so that misconfiguration surfaces instead of being absorbed.
 */
public interface DbucketAdmin {

    /**
     * Creates the bucket when absent; an existing bucket keeps its stored configuration and the
     * current state is returned.
     */
    CreateResult create(BucketSpec spec);

    /**
     * Replaces the bucket capacity, clamping an oversized balance to the new capacity.
     *
     * @return {@code false} when the bucket does not exist
     */
    boolean adjustCapacity(String namespace, String name, BigDecimal capacity);

    /**
     * Replaces the refill rate without touching the current balance.
     *
     * @return {@code false} when the bucket does not exist
     */
    boolean adjustRate(String namespace, String name, BigDecimal rate);

    /**
     * Deletes the bucket.
     *
     * @return {@code false} when the bucket did not exist
     */
    boolean delete(String namespace, String name);
}
