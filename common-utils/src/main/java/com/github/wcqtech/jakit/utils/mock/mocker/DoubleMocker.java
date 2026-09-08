package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces double values with up to two decimal digits, in the range
 * {@code [-9999.99, 9999.99]}, derived deterministically from the mock
 * context seed.
 */
public final class DoubleMocker implements Mocker<Double> {

    private static final long HALF_RANGE_UNSCALED = 999_999L;
    private static final long MODULUS = 2 * HALF_RANGE_UNSCALED + 1;

    @Override
    public Double mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        long unscaled = Math.floorMod(context.getSeed(), MODULUS) - HALF_RANGE_UNSCALED;
        return unscaled / 100.0;
    }
}
