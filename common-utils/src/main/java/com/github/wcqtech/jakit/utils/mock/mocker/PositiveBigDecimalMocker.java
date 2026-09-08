package com.github.wcqtech.jakit.utils.mock.mocker;

import java.math.BigDecimal;
import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces strictly positive {@link BigDecimal} values with scale 2 (two
 * decimal digits), in the range {@code [0.01, 9999.99]}, derived
 * deterministically from the mock context seed.
 */
public final class PositiveBigDecimalMocker implements Mocker<BigDecimal> {

    private static final int SCALE = 2;
    private static final long MODULUS = 999_999L;

    @Override
    public BigDecimal mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        long unscaled = Math.floorMod(context.getSeed(), MODULUS) + 1;
        return BigDecimal.valueOf(unscaled, SCALE);
    }
}
