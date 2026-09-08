package com.github.wcqtech.jakit.utils.mock;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Entry point for deterministic mock-data generation.
 *
 * <p>{@link #mock} builds one value for the given type; {@link #multiMock}
 * returns an unbounded, lazily evaluated stream of values so that callers pick
 * their own final shape with {@code limit} and {@code collect}:
 *
 * <pre>{@code
 * Order order = MockUtils.mock(Order.class);                    // one order
 * List<Order> orders = MockUtils.multiMock(Order.class, 5).toList(); // five orders
 * List<Order> head = MockUtils.multiMock(Order.class).limit(3).toList(); // …or limit the unbounded form
 * }</pre>
 *
 * <p><strong>Determinism:</strong> mocking the same structure twice produces
 * equal values (same JVM, unchanged {@link Mockers} registry and unchanged
 * class declarations). Stream elements differ from each other: element
 * {@code i} is seeded by its position, so {@code multiMock(t).limit(n)} is
 * equal for repeated calls and its prefix of length {@code k} equals
 * {@code multiMock(t).limit(k)}.
 *
 * <p><strong>Field rules</strong> (see the mock design document):
 * <ol>
 * <li>{@link MockIgnore} fields are left untouched (null for references, JVM
 *     default for primitives);</li>
 * <li>{@link MockWith} fields use the annotated {@link Mocker}; a produced
 *     value that is not assignable to the field type fails with an
 *     {@link IllegalArgumentException};</li>
 * <li>otherwise the {@link Mockers} default rules apply;</li>
 * <li>enum slots yield their first constant;</li>
 * <li>collections, maps and arrays are filled with
 *     {@link #DEFAULT_ELEMENT_COUNT} elements whose types are read from the
 *     field's generic declaration;</li>
 * <li>other user-defined classes are constructed via a no-arg constructor and
 *     filled recursively; a reference that would recurse into a type already
 *     being constructed is set to null (cycle guard);</li>
 * <li>JDK types without a registered mocker (e.g. {@code LocalDate},
 *     {@code UUID}), {@code record}s, interfaces, abstract classes and classes
 *     without a no-arg constructor are rejected with an
 *     {@link IllegalArgumentException} instead of being introspected.</li>
 * </ol>
 *
 * <p>The engine keeps no shared mutable state, so concurrent and parallel
 * mocking is safe.
 */
public final class MockUtils {

    /**
     * Number of elements produced for collection and array fields and number
     * of entries produced for map fields.
     */
    public static final int DEFAULT_ELEMENT_COUNT = 3;

    private MockUtils() {
    }

    /**
     * Returns a mock value for the given type.
     *
     * @param type the type to mock; must not be null; primitive roots are
     *             resolved through their wrapper type (e.g. {@code int} yields
     *             an {@link Integer})
     * @param <T> the mocked type
     * @return the mock value, never null
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if the type cannot be mocked (see class
     *         javadoc), including a {@link MockWith} mocker that produces a
     *         value incompatible with the annotated field type
     */
    public static <T> T mock(Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return cast(type, MockEngine.mockRoot(type, -1));
    }

    /**
     * Returns an unbounded stream of distinct mock values of the given type.
     *
     * <p>The stream is lazy and deterministic: element {@code i} is produced
     * on demand from the position seed, independent of the other elements.
     *
     * @param type the type to mock; must not be null
     * @param <T> the mocked type
     * @return a lazy stream of mock values
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if the type cannot be mocked (see class
     *         javadoc)
     */
    public static <T> Stream<T> multiMock(Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return Stream.iterate(0, index -> index + 1)
                .map(index -> cast(type, MockEngine.mockRoot(type, index)));
    }

    /**
     * Returns a stream of exactly {@code size} mock values of the given type.
     *
     * <p>Equivalent to {@link #multiMock(Class) multiMock(type).limit(size)}:
     * element {@code i} is produced from the position seed, so repeated calls
     * with the same size are equal, and the prefix of length {@code k} of a
     * {@code size = n} stream equals the stream produced with {@code size = k}.
     *
     * @param type the type to mock; must not be null
     * @param size number of values to produce; must not be negative
     * @param <T> the mocked type
     * @return a lazy stream of exactly {@code size} mock values
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalArgumentException if {@code size} is negative or the type
     *         cannot be mocked (see class javadoc)
     */
    public static <T> Stream<T> multiMock(Class<T> type, int size) {
        Objects.requireNonNull(type, "type must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative, but was " + size);
        }
        return multiMock(type).limit(size);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Class<T> type, Object value) {
        if (type.isPrimitive()) {
            return (T) value; // boxed by the engine, e.g. int -> Integer
        }
        return type.cast(value);
    }
}
