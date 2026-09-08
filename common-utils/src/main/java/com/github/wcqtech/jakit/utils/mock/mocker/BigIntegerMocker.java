package com.github.wcqtech.jakit.utils.mock.mocker;

import java.math.BigInteger;
import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces positive {@link BigInteger} values in the range
 * {@code [1, 1000000]}, derived deterministically from the mock context seed.
 */
public final class BigIntegerMocker implements Mocker<BigInteger> {

    private static final long MODULUS = 1_000_000L;

    @Override
    public BigInteger mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return BigInteger.valueOf(Math.floorMod(context.getSeed(), MODULUS) + 1);
    }
}
