package com.github.wcqtech.jakit.utils.mock;

/**
 * Produces a single mock value for one target type.
 *
 * <p>Implementations must be <strong>stateless</strong> and thread-safe so
 * that the shared instances held by {@link Mockers} can serve concurrent
 * calls. Mocking is <strong>deterministic</strong>: given the same
 * {@link MockContext}, an implementation must return the same value, so that
 * mocking the same structure twice yields equal values.
 *
 * @param <T> the type of the produced mock value; used for compile-time
 *            convenience only, type compatibility is validated at fill time
 */
@FunctionalInterface
public interface Mocker<T> {

    /**
     * Produces the mock value for the given slot.
     *
     * @param context describes the slot being mocked (field path, position,
     *                deterministic seed); must not be null
     * @return the mock value, may be null only if the mocker deliberately
     *         produces null
     */
    T mock(MockContext context);
}
