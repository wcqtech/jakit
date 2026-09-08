package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces long values in the range {@code [0, 999999999999]}, derived
 * deterministically from the mock context seed.
 */
public final class LongMocker implements Mocker<Long> {

    private static final long MODULUS = 1_000_000_000_000L;

    @Override
    public Long mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return Math.floorMod(context.getSeed(), MODULUS);
    }
}
