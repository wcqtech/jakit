package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces short values in the range {@code [0, 999]}, derived
 * deterministically from the mock context seed.
 */
public final class ShortMocker implements Mocker<Short> {

    private static final long MODULUS = 1_000L;

    @Override
    public Short mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return (short) Math.floorMod(context.getSeed(), MODULUS);
    }
}
