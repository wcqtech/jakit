package com.github.wcqtech.jakit.dbucket.internal;

import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import java.util.Objects;

/**
 * Builds the default {@link Dbucket} implementation; the public entry point is
 * {@link Dbucket#create(BucketStore, DbucketOptions)}.
 *
 * <p>Not part of the public API: the {@link WaitSupport} parameter is a test seam for the polling
 * loop.
 */
public final class DbucketFactory {

    private DbucketFactory() {
    }

    public static Dbucket create(BucketStore store, DbucketOptions options, WaitSupport waitSupport) {
        Objects.requireNonNull(store, "store must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(waitSupport, "waitSupport must not be null");
        return new DefaultDbucket(store, options, waitSupport);
    }
}
