package com.github.wcqtech.jakit.utils.number;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Utility methods for comparing {@link BigDecimal} values.
 *
 * All comparisons use {@link BigDecimal#compareTo(BigDecimal)}, so values are
 * equal when their numeric value is the same even if their scale differs. For
 * example {@code 1.0} and {@code 1.00} compare as equal, unlike
 * {@link BigDecimal#equals(Object)} which also considers the scale.
 *
 * Arguments must not be null; comparison methods reject null instead of
 * assigning a numeric meaning to it.
 */
public final class BigDecimalUtils {

    private BigDecimalUtils() {
    }

    /**
     * Compares two values numerically.
     *
     * @param a the first value; must not be null
     * @param b the second value; must not be null
     * @return a negative integer, zero, or a positive integer when {@code a} is
     *         numerically less than, equal to, or greater than {@code b}
     * @throws NullPointerException if either argument is null
     */
    public static int compare(BigDecimal a, BigDecimal b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");
        return a.compareTo(b);
    }

    /**
     * Returns whether {@code a} is numerically equal to {@code b}.
     *
     * @param a the first value; must not be null
     * @param b the second value; must not be null
     * @return {@code true} when {@code a} is numerically equal to {@code b}
     * @throws NullPointerException if either argument is null
     */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        return compare(a, b) == 0;
    }

    /**
     * Returns whether {@code a} is numerically different from {@code b}.
     *
     * @param a the first value; must not be null
     * @param b the second value; must not be null
     * @return {@code true} when {@code a} is numerically different from
     *         {@code b}
     * @throws NullPointerException if either argument is null
     */
    public static boolean ne(BigDecimal a, BigDecimal b) {
        return compare(a, b) != 0;
    }

    /**
     * Returns whether {@code a} is numerically greater than {@code b}.
     *
     * @param a the first value; must not be null
     * @param b the second value; must not be null
     * @return {@code true} when {@code a} is numerically greater than
     *         {@code b}
     * @throws NullPointerException if either argument is null
     */
    public static boolean gt(BigDecimal a, BigDecimal b) {
        return compare(a, b) > 0;
    }

    /**
     * Returns whether {@code a} is numerically greater than or equal to
     * {@code b}.
     *
     * @param a the first value; must not be null
     * @param b the second value; must not be null
     * @return {@code true} when {@code a} is numerically greater than or equal
     *         to {@code b}
     * @throws NullPointerException if either argument is null
     */
    public static boolean gte(BigDecimal a, BigDecimal b) {
        return compare(a, b) >= 0;
    }

    /**
     * Returns whether {@code a} is numerically less than {@code b}.
     *
     * @param a the first value; must not be null
     * @param b the second value; must not be null
     * @return {@code true} when {@code a} is numerically less than {@code b}
     * @throws NullPointerException if either argument is null
     */
    public static boolean lt(BigDecimal a, BigDecimal b) {
        return compare(a, b) < 0;
    }

    /**
     * Returns whether {@code a} is numerically less than or equal to
     * {@code b}.
     *
     * @param a the first value; must not be null
     * @param b the second value; must not be null
     * @return {@code true} when {@code a} is numerically less than or equal to
     *         {@code b}
     * @throws NullPointerException if either argument is null
     */
    public static boolean lte(BigDecimal a, BigDecimal b) {
        return compare(a, b) <= 0;
    }

    /**
     * Returns whether {@code value} is numerically zero.
     *
     * Negative zero is treated as zero.
     *
     * @param value the value to test; must not be null
     * @return {@code true} when {@code value} is numerically zero
     * @throws NullPointerException if {@code value} is null
     */
    public static boolean isZero(BigDecimal value) {
        return Objects.requireNonNull(value, "value must not be null").signum() == 0;
    }

    /**
     * Returns whether {@code value} is numerically positive.
     *
     * Zero is not positive.
     *
     * @param value the value to test; must not be null
     * @return {@code true} when {@code value} is numerically greater than zero
     * @throws NullPointerException if {@code value} is null
     */
    public static boolean isPositive(BigDecimal value) {
        return Objects.requireNonNull(value, "value must not be null").signum() > 0;
    }

    /**
     * Returns whether {@code value} is numerically negative.
     *
     * Zero is not negative.
     *
     * @param value the value to test; must not be null
     * @return {@code true} when {@code value} is numerically less than zero
     * @throws NullPointerException if {@code value} is null
     */
    public static boolean isNegative(BigDecimal value) {
        return Objects.requireNonNull(value, "value must not be null").signum() < 0;
    }

    /**
     * Returns whether {@code value} lies within the closed range
     * {@code [min, max]}.
     *
     * @param value the value to test; must not be null
     * @param min the lower bound; must not be null and must not exceed
     *            {@code max}
     * @param max the upper bound; must not be null and must not be less than
     *            {@code min}
     * @return {@code true} when {@code min <= value <= max}
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code min} is greater than
     *                                  {@code max}
     */
    public static boolean between(BigDecimal value, BigDecimal min, BigDecimal max) {
        requireRange(min, max);
        return gte(value, min) && lte(value, max);
    }

    /**
     * Returns whether {@code value} lies within the open range
     * {@code (min, max)}.
     *
     * @param value the value to test; must not be null
     * @param min the lower bound; must not be null and must not exceed
     *            {@code max}
     * @param max the upper bound; must not be null and must not be less than
     *            {@code min}
     * @return {@code true} when {@code min < value < max}
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code min} is greater than
     *                                  {@code max}
     */
    public static boolean betweenExclusive(BigDecimal value, BigDecimal min, BigDecimal max) {
        requireRange(min, max);
        return gt(value, min) && lt(value, max);
    }

    /**
     * Restricts {@code value} to the closed range {@code [min, max]}.
     *
     * When {@code value} is below the range, {@code min} is returned; when it
     * is above the range, {@code max} is returned; otherwise {@code value} is
     * returned unchanged.
     *
     * @param value the value to restrict; must not be null
     * @param min the lower bound; must not be null and must not exceed
     *            {@code max}
     * @param max the upper bound; must not be null and must not be less than
     *            {@code min}
     * @return the clamped value
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code min} is greater than
     *                                  {@code max}
     */
    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        Objects.requireNonNull(value, "value must not be null");
        requireRange(min, max);
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    /**
     * Returns the first numerically smallest element in iteration order.
     *
     * @param values the candidates; must not be null and must contain no null
     *               elements
     * @return the first numerically smallest element
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     * @throws IllegalArgumentException if {@code values} is empty
     */
    public static BigDecimal min(Collection<BigDecimal> values) {
        Objects.requireNonNull(values, "values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        BigDecimal min = null;
        for (BigDecimal value : values) {
            Objects.requireNonNull(value, "values must not contain null elements");
            if (min == null || value.compareTo(min) < 0) {
                min = value;
            }
        }
        return min;
    }

    /**
     * Returns the first numerically smallest value.
     *
     * Equivalent to {@link #min(Collection)} over the given values.
     *
     * @param values the candidates; must not be null and must contain no null
     *               elements
     * @return the first numerically smallest value
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     * @throws IllegalArgumentException if {@code values} is empty
     */
    public static BigDecimal min(BigDecimal... values) {
        Objects.requireNonNull(values, "values must not be null");
        return min(Arrays.asList(values));
    }

    /**
     * Returns the first numerically largest element in iteration order.
     *
     * @param values the candidates; must not be null and must contain no null
     *               elements
     * @return the first numerically largest element
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     * @throws IllegalArgumentException if {@code values} is empty
     */
    public static BigDecimal max(Collection<BigDecimal> values) {
        Objects.requireNonNull(values, "values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        BigDecimal max = null;
        for (BigDecimal value : values) {
            Objects.requireNonNull(value, "values must not contain null elements");
            if (max == null || value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }

    /**
     * Returns the first numerically largest value.
     *
     * Equivalent to {@link #max(Collection)} over the given values.
     *
     * @param values the candidates; must not be null and must contain no null
     *               elements
     * @return the first numerically largest value
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     * @throws IllegalArgumentException if {@code values} is empty
     */
    public static BigDecimal max(BigDecimal... values) {
        Objects.requireNonNull(values, "values must not be null");
        return max(Arrays.asList(values));
    }

    /**
     * Returns the sum of all elements.
     *
     * An empty collection sums to {@link BigDecimal#ZERO}.
     *
     * @param values the addends; must not be null and must contain no null
     *               elements
     * @return the sum, or {@link BigDecimal#ZERO} when {@code values} is empty
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     */
    public static BigDecimal sum(Collection<BigDecimal> values) {
        Objects.requireNonNull(values, "values must not be null");
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            Objects.requireNonNull(value, "values must not contain null elements");
            total = total.add(value);
        }
        return total;
    }

    /**
     * Returns the sum of the given values.
     *
     * Equivalent to {@link #sum(Collection)} over the given values.
     *
     * @param values the addends; must not be null and must contain no null
     *               elements
     * @return the sum, or {@link BigDecimal#ZERO} when no values are given
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     */
    public static BigDecimal sum(BigDecimal... values) {
        Objects.requireNonNull(values, "values must not be null");
        return sum(Arrays.asList(values));
    }

    /**
     * Returns the product of all elements.
     *
     * An empty collection multiplies to {@link BigDecimal#ONE}.
     *
     * @param values the factors; must not be null and must contain no null
     *               elements
     * @return the product, or {@link BigDecimal#ONE} when {@code values} is
     *         empty
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     */
    public static BigDecimal mul(Collection<BigDecimal> values) {
        Objects.requireNonNull(values, "values must not be null");
        BigDecimal product = BigDecimal.ONE;
        for (BigDecimal value : values) {
            Objects.requireNonNull(value, "values must not contain null elements");
            product = product.multiply(value);
        }
        return product;
    }

    /**
     * Returns the product of the given values.
     *
     * Equivalent to {@link #mul(Collection)} over the given values.
     *
     * @param values the factors; must not be null and must contain no null
     *               elements
     * @return the product, or {@link BigDecimal#ONE} when no values are given
     * @throws NullPointerException if {@code values} is null or contains a
     *                              null element
     */
    public static BigDecimal mul(BigDecimal... values) {
        Objects.requireNonNull(values, "values must not be null");
        return mul(Arrays.asList(values));
    }

    private static void requireRange(BigDecimal min, BigDecimal max) {
        Objects.requireNonNull(min, "min must not be null");
        Objects.requireNonNull(max, "max must not be null");
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min must not be greater than max");
        }
    }
}
