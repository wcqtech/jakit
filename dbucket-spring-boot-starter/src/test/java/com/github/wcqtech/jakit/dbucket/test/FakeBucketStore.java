package com.github.wcqtech.jakit.dbucket.test;

import com.github.wcqtech.jakit.dbucket.BucketSnapshot;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.ConsumeResult;
import com.github.wcqtech.jakit.dbucket.CreateResult;
import com.github.wcqtech.jakit.dbucket.DbucketStoreException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Scripted {@link BucketStore} test fixture: queue consume results, inspect what the facade asked for.
 */
public final class FakeBucketStore implements BucketStore {

    private static final Instant NOW = Instant.parse("2026-09-10T13:00:00Z");

    /** Queued {@link ConsumeResult} or {@link DbucketStoreException}; empty means INSUFFICIENT. */
    public final Deque<Object> consumeResults = new ArrayDeque<>();
    /** Recorded as {@code namespace/name:tokens}. */
    public final List<String> consumeCalls = new ArrayList<>();
    public final List<Boolean> remainingFlags = new ArrayList<>();
    public final List<BucketSpec> created = new ArrayList<>();
    public Optional<BucketSnapshot> snapshot = Optional.empty();
    public boolean failGet;
    public boolean failDeposit;

    @Override
    public CreateResult createIfAbsent(BucketSpec spec) {
        created.add(spec);
        snapshot = Optional.of(new BucketSnapshot(spec.namespace(), spec.name(), spec.capacity(),
                spec.rate(), spec.initialTokens(), spec.initialTokens(), NOW));
        return CreateResult.created();
    }

    @Override
    public Optional<BucketSnapshot> get(String namespace, String name) {
        if (failGet) {
            throw new DbucketStoreException("get failed");
        }
        return snapshot;
    }

    @Override
    public ConsumeResult tryConsume(String namespace, String name, long tokens, boolean withRemaining) {
        consumeCalls.add(namespace + "/" + name + ":" + tokens);
        remainingFlags.add(withRemaining);
        Object next = consumeResults.poll();
        if (next instanceof DbucketStoreException failure) {
            throw failure;
        }
        return next == null ? ConsumeResult.insufficient() : (ConsumeResult) next;
    }

    @Override
    public boolean deposit(String namespace, String name, long tokens) {
        if (failDeposit) {
            throw new DbucketStoreException("deposit failed");
        }
        return true;
    }

    @Override
    public boolean adjustCapacity(String namespace, String name, BigDecimal capacity) {
        return true;
    }

    @Override
    public boolean adjustRate(String namespace, String name, BigDecimal rate) {
        return true;
    }

    @Override
    public boolean delete(String namespace, String name) {
        return true;
    }
}
