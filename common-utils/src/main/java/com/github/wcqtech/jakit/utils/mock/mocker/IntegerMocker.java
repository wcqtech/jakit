package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces integer values in the range {@code [0, 999999]}, derived
 * deterministically from the mock context seed.
 */
public final class IntegerMocker implements Mocker<Integer> {

    private static final long MODULUS = 1_000_000L;

    @Override
    public Integer mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return (int) Math.floorMod(context.getSeed(), MODULUS);
    }
}
