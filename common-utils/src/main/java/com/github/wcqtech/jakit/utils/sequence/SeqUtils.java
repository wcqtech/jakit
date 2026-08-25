package com.github.wcqtech.jakit.utils.sequence;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility methods for assigning sequence numbers to collection elements.
 *
 * Each invocation owns its sequence counter: the first element receives
 * {@code start}, and each following element receives the previous value plus
 * {@code step}. Assignment order follows the iteration order of the collection,
 * so callers that need a deterministic order should pass an ordered collection
 * such as a {@code List}.
 *
 * These methods are not thread-safe for concurrent invocations over the same
 * collection. Concurrent calls can assign duplicate or interleaved sequence
 * values; synchronize externally when a collection is shared between threads.
 */
public final class SeqUtils {

    private static final Consumer<Object> NOOP = element -> {
    };

    private SeqUtils() {
    }

    /**
     * Assigns a sequence number to every element and invokes the visitor after
     * each assignment.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param convert converts the raw integer sequence value into the value
     *                accepted by the setter; must not be null
     * @param start the sequence value assigned to the first element
     * @param step the difference between two consecutive sequence values; use a
     *             negative value for a descending sequence
     * @param visitor invoked with each element after its sequence is assigned;
     *                must not be null
     * @param <T> the element type
     * @param <S> the sequence value type accepted by the setter
     * @throws NullPointerException if any argument is null
     */
    public static <T, S> void sequence(Collection<T> targets, BiConsumer<T, S> setter, Function<Integer, S> convert,
                                       int start, int step, Consumer<? super T> visitor) {
        Objects.requireNonNull(targets, "targets must not be null");
        Objects.requireNonNull(setter, "setter must not be null");
        Objects.requireNonNull(convert, "convert must not be null");
        Objects.requireNonNull(visitor, "visitor must not be null");
        int seq = start;
        for (T element : targets) {
            setter.accept(element, convert.apply(seq));
            visitor.accept(element);
            seq += step;
        }
    }

    /**
     * Assigns a sequence number to every element.
     *
     * Equivalent to
     * {@link #sequence(Collection, BiConsumer, Function, int, int, Consumer)}
     * with a no-op visitor.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param convert converts the raw integer sequence value into the value
     *                accepted by the setter; must not be null
     * @param start the sequence value assigned to the first element
     * @param step the difference between two consecutive sequence values; use a
     *             negative value for a descending sequence
     * @param <T> the element type
     * @param <S> the sequence value type accepted by the setter
     * @throws NullPointerException if any argument is null
     */
    public static <T, S> void sequence(Collection<T> targets, BiConsumer<T, S> setter, Function<Integer, S> convert,
                                       int start, int step) {
        sequence(targets, setter, convert, start, step, NOOP);
    }

    /**
     * Assigns integer sequence values to every element and invokes the visitor
     * after each assignment.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param start the sequence value assigned to the first element
     * @param step the difference between two consecutive sequence values; use a
     *             negative value for a descending sequence
     * @param visitor invoked with each element after its sequence is assigned;
     *                must not be null
     * @param <T> the element type
     * @throws NullPointerException if any argument is null
     */
    public static <T> void sequence(Collection<T> targets, BiConsumer<T, Integer> setter, int start, int step,
                                    Consumer<? super T> visitor) {
        sequence(targets, setter, Function.identity(), start, step, visitor);
    }

    /**
     * Assigns integer sequence values to every element.
     *
     * Equivalent to
     * {@link #sequence(Collection, BiConsumer, int, int, Consumer)} with a
     * no-op visitor.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param start the sequence value assigned to the first element
     * @param step the difference between two consecutive sequence values; use a
     *             negative value for a descending sequence
     * @param <T> the element type
     * @throws NullPointerException if any argument is null
     */
    public static <T> void sequence(Collection<T> targets, BiConsumer<T, Integer> setter, int start, int step) {
        sequence(targets, setter, start, step, NOOP);
    }

    /**
     * Assigns a sequence number to every element, starting at {@code 1} with
     * step {@code 1}.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param convert converts the raw integer sequence value into the value
     *                accepted by the setter; must not be null
     * @param <T> the element type
     * @param <S> the sequence value type accepted by the setter
     * @throws NullPointerException if any argument is null
     */
    public static <T, S> void sequence(Collection<T> targets, BiConsumer<T, S> setter, Function<Integer, S> convert) {
        sequence(targets, setter, convert, 1, 1);
    }

    /**
     * Assigns integer sequence values starting at {@code 1} with step
     * {@code 1}.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param <T> the element type
     * @throws NullPointerException if any argument is null
     */
    public static <T> void sequence(Collection<T> targets, BiConsumer<T, Integer> setter) {
        sequence(targets, setter, 1, 1);
    }

    /**
     * Assigns a sequence number to every element, starting at {@code 1} with
     * step {@code 1}, and invokes the visitor after each assignment.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param convert converts the raw integer sequence value into the value
     *                accepted by the setter; must not be null
     * @param visitor invoked with each element after its sequence is assigned;
     *                must not be null
     * @param <T> the element type
     * @param <S> the sequence value type accepted by the setter
     * @throws NullPointerException if any argument is null
     */
    public static <T, S> void sequence(Collection<T> targets, BiConsumer<T, S> setter, Function<Integer, S> convert,
                                       Consumer<? super T> visitor) {
        sequence(targets, setter, convert, 1, 1, visitor);
    }

    /**
     * Assigns integer sequence values starting at {@code 1} with step
     * {@code 1}, and invokes the visitor after each assignment.
     *
     * Equivalent to
     * {@link #sequence(Collection, BiConsumer, int, int, Consumer)} with
     * {@code start = 1} and {@code step = 1}.
     *
     * @param targets the elements to sequence; must not be null; an empty
     *                collection is a no-op
     * @param setter writes the sequence value into an element; must not be null
     * @param visitor invoked with each element after its sequence is assigned;
     *                must not be null
     * @param <T> the element type
     * @throws NullPointerException if any argument is null
     */
    public static <T> void sequence(Collection<T> targets, BiConsumer<T, Integer> setter, Consumer<? super T> visitor) {
        sequence(targets, setter, Function.identity(), 1, 1, visitor);
    }
}
