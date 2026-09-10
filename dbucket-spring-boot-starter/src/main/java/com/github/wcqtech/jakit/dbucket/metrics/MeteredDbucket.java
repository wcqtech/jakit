package com.github.wcqtech.jakit.dbucket.metrics;

import com.github.wcqtech.jakit.dbucket.AcquireResult;
import com.github.wcqtech.jakit.dbucket.Bucket;
import com.github.wcqtech.jakit.dbucket.BucketSnapshot;
import com.github.wcqtech.jakit.dbucket.Dbucket;
import com.github.wcqtech.jakit.dbucket.DbucketAdmin;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link Dbucket} decorator that records Micrometer metrics for the acquire path.
 *
 * <p>Meters:
 *
 * <ul>
 *   <li>{@code dbucket.acquire} (counter) tagged {@code namespace}, {@code bucket},
 *       {@code outcome} = {@code success|insufficient|not_found|degraded}</li>
 *   <li>{@code dbucket.acquire.duration} (timer) with the same tags, recording the time the facade
 *       spent (including a blocking wait)</li>
 *   <li>{@code dbucket.acquire.errors} (counter) tagged {@code namespace}, {@code bucket}</li>
 * </ul>
 *
 * <p>Bucket names become tag values: keep them bounded (per tenant or per endpoint, not per user) or
 * leave metrics disabled.
 */
public final class MeteredDbucket implements Dbucket {

    private final Dbucket delegate;
    private final MeterRegistry registry;

    private MeteredDbucket(Dbucket delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    /** Wraps a facade so that every acquire is measured. */
    public static Dbucket wrap(Dbucket delegate, MeterRegistry registry) {
        return new MeteredDbucket(Objects.requireNonNull(delegate, "delegate must not be null"),
                Objects.requireNonNull(registry, "registry must not be null"));
    }

    @Override
    public Bucket bucket(String name) {
        return new MeteredBucket(delegate.bucket(name));
    }

    @Override
    public Bucket bucket(String namespace, String name) {
        return new MeteredBucket(delegate.bucket(namespace, name));
    }

    @Override
    public DbucketAdmin admin() {
        return delegate.admin();
    }

    private final class MeteredBucket implements Bucket {

        private final Bucket delegate;

        private MeteredBucket(Bucket delegate) {
            this.delegate = delegate;
        }

        @Override
        public AcquireResult tryAcquire(long tokens) {
            return record(() -> delegate.tryAcquire(tokens));
        }

        @Override
        public AcquireResult acquireResult(long tokens, Duration timeout) {
            return record(() -> delegate.acquireResult(tokens, timeout));
        }

        @Override
        public boolean acquire(long tokens, Duration timeout) {
            return acquireResult(tokens, timeout).isSuccess();
        }

        @Override
        public Optional<BucketSnapshot> snapshot() {
            return delegate.snapshot();
        }

        @Override
        public boolean deposit(long tokens) {
            return delegate.deposit(tokens);
        }

        @Override
        public String namespace() {
            return delegate.namespace();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        private AcquireResult record(Supplier<AcquireResult> call) {
            AcquireResult result;
            try {
                result = call.get();
            } catch (RuntimeException e) {
                registry.counter("dbucket.acquire.errors",
                        "namespace", delegate.namespace(), "bucket", delegate.name()).increment();
                throw e;
            }
            String outcome = outcome(result);
            registry.counter("dbucket.acquire",
                            "namespace", delegate.namespace(), "bucket", delegate.name(),
                            "outcome", outcome)
                    .increment();
            registry.timer("dbucket.acquire.duration",
                            "namespace", delegate.namespace(), "bucket", delegate.name(),
                            "outcome", outcome)
                    .record(result.waited());
            return result;
        }

        private String outcome(AcquireResult result) {
            if (result.degraded()) {
                return "degraded";
            }
            return switch (result.outcome()) {
                case SUCCESS -> "success";
                case INSUFFICIENT -> "insufficient";
                case NOT_FOUND -> "not_found";
            };
        }
    }
}
