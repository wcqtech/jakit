package com.github.wcqtech.jakit.dbucket.internal;

import com.github.wcqtech.jakit.dbucket.AcquireResult;
import com.github.wcqtech.jakit.dbucket.Bucket;
import com.github.wcqtech.jakit.dbucket.BucketSnapshot;
import com.github.wcqtech.jakit.dbucket.BucketSpec;
import com.github.wcqtech.jakit.dbucket.BucketStore;
import com.github.wcqtech.jakit.dbucket.ConsumeResult;
import com.github.wcqtech.jakit.dbucket.CreateResult;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketAdmin;
import com.github.wcqtech.jakit.dbucket.DbucketOptions;
import com.github.wcqtech.jakit.dbucket.DbucketStoreException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default policy layer on top of {@link BucketStore}: auto-creation, fail-open, exact remaining and
 * the polling acquire.
 *
 * <p>Package private on purpose: users go through {@link Dbucket}, {@link Bucket} and
 * {@link DbucketAdmin}.
 */
final class DefaultDbucket implements Dbucket {

    private static final Log LOG = LogFactory.getLog(DefaultDbucket.class);

    private final BucketStore store;
    private final DbucketOptions options;
    private final WaitSupport waitSupport;
    private final Random random = new Random();

    DefaultDbucket(BucketStore store, DbucketOptions options, WaitSupport waitSupport) {
        this.store = store;
        this.options = options;
        this.waitSupport = waitSupport;
    }

    @Override
    public Bucket bucket(String name) {
        return bucket(options.defaultNamespace(), name);
    }

    @Override
    public Bucket bucket(String namespace, String name) {
        Values.requireText("namespace", namespace, BucketSpec.NAMESPACE_MAX_LENGTH);
        Values.requireText("name", name, BucketSpec.NAME_MAX_LENGTH);
        return new BucketHandle(namespace, name);
    }

    @Override
    public DbucketAdmin admin() {
        return new AdminHandle();
    }

    // ---------------------------------------------------------------- policy

    private AcquireResult acquireOnce(BucketKey key, long tokens) {
        requirePositive(tokens);
        long start = waitSupport.nanoTime();
        try {
            ConsumeResult result = store.tryConsume(key.namespace(), key.name(), tokens,
                    options.exactRemaining());
            if (result.outcome() == ConsumeResult.Outcome.NOT_FOUND && options.autoCreate()) {
                result = autoCreateAndRetry(key, tokens);
            }
            return toAcquireResult(result, start);
        } catch (DbucketStoreException failure) {
            if (!options.failOpen()) {
                throw failure;
            }
            LOG.error("dbucket acquire degraded to fail-open, traffic allowed through for bucket "
                    + key.namespace() + "/" + key.name() + ": " + failure.getMessage(), failure);
            return AcquireResult.degraded(elapsed(start));
        }
    }

    private AcquireResult acquireBlocking(BucketKey key, long tokens, Duration timeout) {
        long start = waitSupport.nanoTime();
        long deadline = start + Math.max(timeout.toNanos(), 0L);
        while (true) {
            AcquireResult result = acquireOnce(key, tokens);
            if (result.isSuccess()) {
                return withTotalWait(result, elapsed(start));
            }
            if (result.outcome() == AcquireResult.Outcome.NOT_FOUND) {
                // delete-priority semantics: waiting cannot help, and no compensation is attempted
                return withTotalWait(result, elapsed(start));
            }
            long remaining = deadline - waitSupport.nanoTime();
            if (remaining <= 0) {
                return AcquireResult.insufficient(elapsed(start));
            }
            try {
                waitSupport.sleep(nextDelay(remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AcquireResult.insufficient(elapsed(start));
            }
        }
    }

    /** Reports the whole wait, not just the duration of the last attempt. */
    private static AcquireResult withTotalWait(AcquireResult result, Duration waited) {
        return new AcquireResult(result.outcome(), result.remaining(), waited, result.degraded());
    }

    /** Poll interval clamped to the remaining budget, optionally jittered by 50%..150%. */
    private Duration nextDelay(long remainingNanos) {
        long delay = Math.min(options.pollInterval().toNanos(), remainingNanos);
        if (options.jitter() && delay > 1) {
            delay = Math.min(delay / 2 + (long) (random.nextDouble() * delay), remainingNanos);
        }
        return Duration.ofNanos(delay);
    }

    private ConsumeResult autoCreateAndRetry(BucketKey key, long tokens) {
        BucketSpec spec = Objects.requireNonNull(options.specFactory().create(key.namespace(), key.name()),
                "bucket spec factory returned null");
        if (!key.namespace().equals(spec.namespace()) || !key.name().equals(spec.name())) {
            throw new IllegalStateException("bucket spec factory returned " + spec.namespace() + "/"
                    + spec.name() + " while auto-creating " + key.namespace() + "/" + key.name());
        }
        CreateResult created = store.createIfAbsent(spec);
        if (!created.isCreated() && created.snapshot() != null) {
            warnOnConfigurationDrift(spec, created.snapshot());
        }
        return store.tryConsume(key.namespace(), key.name(), tokens, options.exactRemaining());
    }

    private void warnOnConfigurationDrift(BucketSpec spec, BucketSnapshot existing) {
        if (existing.capacity().compareTo(spec.capacity()) != 0
                || existing.rate().compareTo(spec.rate()) != 0) {
            LOG.warn("dbucket bucket " + spec.namespace() + "/" + spec.name()
                    + " already exists with capacity=" + existing.capacity().toPlainString()
                    + ", rate=" + existing.rate().toPlainString()
                    + " and keeps that configuration (requested capacity="
                    + spec.capacity().toPlainString() + ", rate=" + spec.rate().toPlainString() + ")");
        }
    }

    private AcquireResult toAcquireResult(ConsumeResult result, long start) {
        Duration waited = elapsed(start);
        return switch (result.outcome()) {
            case SUCCESS -> result.hasRemaining()
                    ? AcquireResult.success(result.remaining(), waited)
                    : AcquireResult.success(waited);
            case INSUFFICIENT -> AcquireResult.insufficient(waited);
            case NOT_FOUND -> AcquireResult.notFound(waited);
        };
    }

    private Duration elapsed(long start) {
        return Duration.ofNanos(Math.max(waitSupport.nanoTime() - start, 0L));
    }

    private static void requirePositive(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be > 0 but was " + tokens);
        }
    }

    private record BucketKey(String namespace, String name) {
    }

    private final class BucketHandle implements Bucket {

        private final String namespace;
        private final String name;

        private BucketHandle(String namespace, String name) {
            this.namespace = namespace;
            this.name = name;
        }

        @Override
        public AcquireResult tryAcquire(long tokens) {
            return acquireOnce(new BucketKey(namespace, name), tokens);
        }

        @Override
        public boolean acquire(long tokens, Duration timeout) {
            return acquireResult(tokens, timeout).isSuccess();
        }

        @Override
        public AcquireResult acquireResult(long tokens, Duration timeout) {
            Objects.requireNonNull(timeout, "timeout must not be null");
            return acquireBlocking(new BucketKey(namespace, name), tokens, timeout);
        }

        @Override
        public Optional<BucketSnapshot> snapshot() {
            return store.get(namespace, name);
        }

        @Override
        public boolean deposit(long tokens) {
            requirePositive(tokens);
            return store.deposit(namespace, name, tokens);
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private final class AdminHandle implements DbucketAdmin {

        @Override
        public CreateResult create(BucketSpec spec) {
            return store.createIfAbsent(Objects.requireNonNull(spec, "spec must not be null"));
        }

        @Override
        public boolean adjustCapacity(String namespace, String name, BigDecimal capacity) {
            return store.adjustCapacity(namespace, name, capacity);
        }

        @Override
        public boolean adjustRate(String namespace, String name, BigDecimal rate) {
            return store.adjustRate(namespace, name, rate);
        }

        @Override
        public boolean delete(String namespace, String name) {
            return store.delete(namespace, name);
        }
    }
}
