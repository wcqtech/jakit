package com.github.wcqtech.jakit.dbucket;

import com.github.wcqtech.jakit.dbucket.internal.Values;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * Tunable policy of a {@link Dbucket}: auto-creation, failure handling, exact remaining and the
 * polling behaviour of the blocking acquire.
 *
 * <p>Immutable; build with {@link #builder()}. Defaults are conservative and mirror the design
 * document: fail-open on, exact remaining off, 20 ms polling with jitter, auto-creation off until a
 * spec factory is supplied.
 */
public final class DbucketOptions {

    /** Default namespace used by {@link Dbucket#bucket(String)}. */
    public static final String DEFAULT_NAMESPACE = "default";

    /** Default polling interval of the blocking acquire. */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(20);

    private final String defaultNamespace;
    private final boolean autoCreate;
    private final BucketSpecFactory specFactory;
    private final boolean failOpen;
    private final boolean exactRemaining;
    private final Duration pollInterval;
    private final boolean jitter;

    private DbucketOptions(Builder builder) {
        this.defaultNamespace = Values.requireText("defaultNamespace", builder.defaultNamespace,
                BucketSpec.NAMESPACE_MAX_LENGTH);
        this.autoCreate = builder.autoCreate;
        this.specFactory = builder.specFactory;
        this.failOpen = builder.failOpen;
        this.exactRemaining = builder.exactRemaining;
        Duration interval = Objects.requireNonNull(builder.pollInterval, "pollInterval must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must be > 0 but was " + interval);
        }
        this.pollInterval = interval;
        this.jitter = builder.jitter;
        if (autoCreate && specFactory == null) {
            throw new IllegalStateException("auto-create requires a bucket spec factory: "
                    + "call autoCreateSpec(...) or disable auto-create");
        }
    }

    /** Builds options with the documented defaults. */
    public static DbucketOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String defaultNamespace() {
        return defaultNamespace;
    }

    /** Whether a missing bucket is created on the acquire path. */
    public boolean autoCreate() {
        return autoCreate;
    }

    /** Factory used to build the bucket definition during auto-creation; {@code null} when disabled. */
    public BucketSpecFactory specFactory() {
        return specFactory;
    }

    /**
     * Whether a storage failure on the acquire path is absorbed (traffic is allowed through, an ERROR
     * is logged and {@link AcquireResult#degraded()} is set). Reads, deposits and admin calls never
     * fail-open.
     */
    public boolean failOpen() {
        return failOpen;
    }

    /** Whether acquire asks the storage for the exact remaining balance. */
    public boolean exactRemaining() {
        return exactRemaining;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    /** Whether the polling interval is randomized to avoid thundering herds. */
    public boolean jitter() {
        return jitter;
    }

    /** Builds the bucket definition used when auto-creating a missing bucket. */
    @FunctionalInterface
    public interface BucketSpecFactory {

        BucketSpec create(String namespace, String name);
    }

    /** Mutable builder with the documented defaults. */
    public static final class Builder {

        private String defaultNamespace = DEFAULT_NAMESPACE;
        private boolean autoCreate;
        private BucketSpecFactory specFactory;
        private boolean failOpen = true;
        private boolean exactRemaining;
        private Duration pollInterval = DEFAULT_POLL_INTERVAL;
        private boolean jitter = true;

        private Builder() {
        }

        public Builder defaultNamespace(String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            return this;
        }

        public Builder autoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        /** Supplies the auto-create definition and enables auto-creation. */
        public Builder autoCreateSpec(BucketSpecFactory specFactory) {
            this.specFactory = specFactory;
            this.autoCreate = specFactory != null;
            return this;
        }

        /** Auto-creates buckets as full buckets with the given capacity and refill rate. */
        public Builder autoCreateSpec(BigDecimal capacity, BigDecimal rate) {
            return autoCreateSpec(capacity, rate, BucketSpec.InitialState.FULL);
        }

        /** Auto-creates buckets with the given capacity, refill rate and initial state. */
        public Builder autoCreateSpec(BigDecimal capacity, BigDecimal rate,
                                      BucketSpec.InitialState initialState) {
            BigDecimal validatedCapacity = Values.requireDecimal("capacity", capacity, false);
            BigDecimal validatedRate = Values.requireDecimal("rate", rate, true);
            Objects.requireNonNull(initialState, "initialState must not be null");
            return autoCreateSpec((namespace, name) ->
                    new BucketSpec(namespace, name, validatedCapacity, validatedRate, initialState));
        }

        public Builder failOpen(boolean failOpen) {
            this.failOpen = failOpen;
            return this;
        }

        public Builder exactRemaining(boolean exactRemaining) {
            this.exactRemaining = exactRemaining;
            return this;
        }

        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        public DbucketOptions build() {
            return new DbucketOptions(this);
        }
    }
}
