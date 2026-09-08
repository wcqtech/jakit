package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces byte values in the range {@code [0, 99]}, derived deterministically
 * from the mock context seed.
 */
public final class ByteMocker implements Mocker<Byte> {

    private static final long MODULUS = 100L;

    @Override
    public Byte mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return (byte) Math.floorMod(context.getSeed(), MODULUS);
    }
}
