package com.github.wcqtech.jakit.utils.mock.mocker;

import java.util.Objects;

import com.github.wcqtech.jakit.utils.mock.MockContext;
import com.github.wcqtech.jakit.utils.mock.Mocker;

/**
 * Produces uppercase ASCII letters in the range {@code 'A'..'Z'}, derived
 * deterministically from the mock context seed.
 */
public final class CharacterMocker implements Mocker<Character> {

    private static final long MODULUS = 26L;

    @Override
    public Character mock(MockContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return (char) ('A' + Math.floorMod(context.getSeed(), MODULUS));
    }
}
