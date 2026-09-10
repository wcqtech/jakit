package com.github.wcqtech.jakit.dbucket;

import com.github.wcqtech.jakit.dbucket.internal.DbucketFactory;
import com.github.wcqtech.jakit.dbucket.internal.WaitSupport;

/**
 * Entry point of the token bucket facade.
 *
 * <p>Resolve handles with {@link #bucket(String)} (default namespace) or
 * {@link #bucket(String, String)} and acquire tokens through them; use {@link #admin()} for
 * lifecycle operations. All waiting, retrying, auto-creation and fail-open decisions live behind this
 * facade, so a {@link BucketStore} implementation never has to reimplement them.
 */
public interface Dbucket {

    /** Handle of a bucket in the configured default namespace. */
    Bucket bucket(String name);

    /** Handle of a bucket in an explicit namespace. */
    Bucket bucket(String namespace, String name);

    /** Lifecycle operations: create, adjust capacity/rate and delete. */
    DbucketAdmin admin();

    /**
     * Creates a facade with default options: no auto-creation, fail-open enabled, exact remaining
     * disabled and a 20 ms polling interval.
     */
    static Dbucket create(BucketStore store) {
        return create(store, DbucketOptions.defaults());
    }

    /** Creates a facade with explicit options. */
    static Dbucket create(BucketStore store, DbucketOptions options) {
        return DbucketFactory.create(store, options, WaitSupport.system());
    }
}
