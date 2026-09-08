package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces {@code true} for even seeds and {@code false} for odd seeds,
 * deterministically from the mock context seed.
 */
public final class BooleanMocker implements Mocker<Boolean> {

    @Override
    public Boolean mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return Math.floorMod(context.getSeed(), 2L) == 0L;
    }
}
