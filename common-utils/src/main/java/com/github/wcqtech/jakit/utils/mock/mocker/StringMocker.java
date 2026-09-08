package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces short, readable string values of the form {@code mock-<suffix>},
 * derived deterministically from the mock context seed. The length never
 * exceeds {@link #MAX_LENGTH}.
 */
public final class StringMocker implements Mocker<String> {

    /**
     * Upper bound for the length of a produced value.
     */
    public static final int MAX_LENGTH = 16;

    private static final long SUFFIX_MODULUS = 1_000_000_000L; // base-36: at most 6 chars

    @Override
    public String mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        long suffix = Math.floorMod(context.getSeed(), SUFFIX_MODULUS);
        return "mock-" + Long.toString(suffix, 36);
    }
}
