package com.github.wcqtech.jakit.dbucket;

import com.github.wcqtech.jakit.dbucket.internal.Values;
import java.math.BigDecimal;

/**
 * Immutable description of a bucket used when creating it.
 *
 * <p>{@code capacity} is the burst ceiling, {@code rate} is the refill speed in tokens per second
 * (may be {@code 0} for a manually filled bucket) and {@code initialState} decides whether a newly
 * created bucket starts full or empty.
 *
 * <p>Values must fit the storage columns {@code NUMERIC(20,6)} / {@code DECIMAL(20,6)}: at most
 * {@value #TOKEN_SCALE} fractional digits and {@value #TOKEN_INTEGER_DIGITS} whole-token digits.
 * Trailing zeros are canonicalized, so {@code 10.0000000} is stored as {@code 10}.
 *
 * @param namespace   bucket namespace, at most {@value #NAMESPACE_MAX_LENGTH} characters
 * @param name        bucket name, at most {@value #NAME_MAX_LENGTH} characters
 * @param capacity    burst ceiling, strictly positive
 * @param rate        refill speed in tokens per second, zero or positive
 * @param initialState initial token state of a newly created bucket
 */
public record BucketSpec(String namespace,
                         String name,
                         BigDecimal capacity,
                         BigDecimal rate,
                         InitialState initialState) {

    /** Initial token state of a newly created bucket. */
    public enum InitialState {

        /** The bucket starts at {@code capacity} and can be consumed immediately. */
        FULL,

        /** The bucket starts empty and only fills over time (or by manual deposit). */
        EMPTY
    }

    /** Maximum length of {@link #namespace()}, matching {@code VARCHAR(64)}. */
    public static final int NAMESPACE_MAX_LENGTH = 64;

    /** Maximum length of {@link #name()}, matching {@code VARCHAR(128)}. */
    public static final int NAME_MAX_LENGTH = 128;

    /** Fractional digits supported by the token columns. */
    public static final int TOKEN_SCALE = Values.TOKEN_SCALE;

    /** Whole-token digits supported by the token columns. */
    public static final int TOKEN_INTEGER_DIGITS = Values.TOKEN_INTEGER_DIGITS;

    public BucketSpec {
        namespace = Values.requireText("namespace", namespace, NAMESPACE_MAX_LENGTH);
        name = Values.requireText("name", name, NAME_MAX_LENGTH);
        capacity = Values.requireDecimal("capacity", capacity, false);
        rate = Values.requireDecimal("rate", rate, true);
        if (initialState == null) {
            throw new IllegalArgumentException("initialState must not be null");
        }
    }

    /**
     * Creates a spec for a bucket that starts full.
     */
    public static BucketSpec of(String namespace, String name, BigDecimal capacity, BigDecimal rate) {
        return new BucketSpec(namespace, name, capacity, rate, InitialState.FULL);
    }

    /**
     * Creates a spec with an explicit initial state.
     */
    public static BucketSpec of(String namespace,
                                String name,
                                BigDecimal capacity,
                                BigDecimal rate,
                                InitialState initialState) {
        return new BucketSpec(namespace, name, capacity, rate, initialState);
    }

    /**
     * Token count a newly created bucket starts with: {@code capacity} when full, {@code 0} when
     * empty.
     */
    public BigDecimal initialTokens() {
        return initialState == InitialState.FULL ? capacity : BigDecimal.ZERO;
    }
}
