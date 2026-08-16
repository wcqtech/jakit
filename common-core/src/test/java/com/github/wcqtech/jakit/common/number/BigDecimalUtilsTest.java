package com.github.wcqtech.jakit.common.number;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigDecimalUtilsTest {

    private static final BigDecimal ONE = new BigDecimal("1.0");
    private static final BigDecimal ONE_CENT = new BigDecimal("1.00");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal ONE_AND_A_HALF = new BigDecimal("1.5");

    @Test
    void compareOrdersByNumericValue() {
        assertEquals(0, BigDecimalUtils.compare(ONE, ONE_CENT));
        assertEquals(-1, Integer.signum(BigDecimalUtils.compare(ONE, TWO)));
        assertEquals(1, Integer.signum(BigDecimalUtils.compare(TWO, ONE)));
    }

    @Test
    void eqIgnoresScale() {
        assertTrue(BigDecimalUtils.eq(ONE, ONE_CENT));
        assertTrue(BigDecimalUtils.eq(new BigDecimal("-0.00"), BigDecimal.ZERO));
        assertFalse(BigDecimalUtils.eq(ONE, TWO));
    }

    @Test
    void neDetectsDifferentValues() {
        assertTrue(BigDecimalUtils.ne(ONE, TWO));
        assertFalse(BigDecimalUtils.ne(ONE, ONE_CENT));
    }

    @Test
    void gtAndGteCompareValues() {
        assertTrue(BigDecimalUtils.gt(TWO, ONE));
        assertFalse(BigDecimalUtils.gt(ONE, ONE_CENT));
        assertTrue(BigDecimalUtils.gte(ONE, ONE_CENT));
        assertTrue(BigDecimalUtils.gte(TWO, ONE));
        assertFalse(BigDecimalUtils.gte(ONE, TWO));
    }

    @Test
    void ltAndLteCompareValues() {
        assertTrue(BigDecimalUtils.lt(ONE, TWO));
        assertFalse(BigDecimalUtils.lt(ONE, ONE_CENT));
        assertTrue(BigDecimalUtils.lte(ONE, ONE_CENT));
        assertTrue(BigDecimalUtils.lte(ONE, TWO));
        assertFalse(BigDecimalUtils.lte(TWO, ONE));
    }

    @Test
    void comparisonMethodsRejectNull() {
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.compare(null, ONE));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.compare(ONE, null));

        List<BiFunction<BigDecimal, BigDecimal, Boolean>> predicates =
                List.of(BigDecimalUtils::eq, BigDecimalUtils::ne, BigDecimalUtils::gt,
                        BigDecimalUtils::gte, BigDecimalUtils::lt, BigDecimalUtils::lte);
        for (BiFunction<BigDecimal, BigDecimal, Boolean> predicate : predicates) {
            assertThrows(NullPointerException.class, () -> predicate.apply(null, ONE));
            assertThrows(NullPointerException.class, () -> predicate.apply(ONE, null));
        }
    }

    @Test
    void betweenIncludesBothEndpoints() {
        assertTrue(BigDecimalUtils.between(ONE, ONE, TWO));
        assertTrue(BigDecimalUtils.between(TWO, ONE, TWO));
        assertTrue(BigDecimalUtils.between(ONE_AND_A_HALF, ONE, TWO));
        assertTrue(BigDecimalUtils.between(ONE, ONE_CENT, ONE_CENT));
        assertFalse(BigDecimalUtils.between(BigDecimal.ZERO, ONE, TWO));
        assertFalse(BigDecimalUtils.between(THREE, ONE, TWO));
    }

    @Test
    void betweenExclusiveExcludesBothEndpoints() {
        assertTrue(BigDecimalUtils.betweenExclusive(ONE_AND_A_HALF, ONE, TWO));
        assertFalse(BigDecimalUtils.betweenExclusive(ONE, ONE, TWO));
        assertFalse(BigDecimalUtils.betweenExclusive(TWO, ONE, TWO));
        assertFalse(BigDecimalUtils.betweenExclusive(BigDecimal.ZERO, ONE, TWO));
        assertFalse(BigDecimalUtils.betweenExclusive(THREE, ONE, TWO));
    }

    @Test
    void betweenRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> BigDecimalUtils.between(ONE, TWO, ONE));
        assertThrows(IllegalArgumentException.class,
                () -> BigDecimalUtils.betweenExclusive(ONE, TWO, ONE));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.between(ONE, null, TWO));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.between(ONE, ONE, null));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.between(null, ONE, TWO));
    }

    @Test
    void clampRestrictsToRange() {
        assertSame(ONE, BigDecimalUtils.clamp(BigDecimal.ZERO, ONE, TWO));
        assertSame(TWO, BigDecimalUtils.clamp(THREE, ONE, TWO));
        assertSame(ONE_AND_A_HALF, BigDecimalUtils.clamp(ONE_AND_A_HALF, ONE, TWO));
        assertSame(ONE, BigDecimalUtils.clamp(ONE, ONE, TWO));
    }

    @Test
    void clampRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> BigDecimalUtils.clamp(ONE, TWO, ONE));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.clamp(null, ONE, TWO));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.clamp(ONE, null, TWO));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.clamp(ONE, ONE, null));
    }

    @Test
    void minSelectsSmallestValue() {
        assertSame(ONE_CENT, BigDecimalUtils.min(List.of(ONE_CENT, ONE, TWO)));
        assertEquals(ONE, BigDecimalUtils.min(TWO, ONE, THREE));
        assertEquals(THREE, BigDecimalUtils.max(List.of(ONE, TWO, THREE)));
    }

    @Test
    void maxSelectsLargestValue() {
        assertSame(TWO, BigDecimalUtils.max(ONE, TWO, ONE_AND_A_HALF));
        assertSame(TWO, BigDecimalUtils.max(List.of(ONE_AND_A_HALF, ONE, TWO)));
        assertEquals(THREE, BigDecimalUtils.max(Arrays.asList(ONE, THREE, TWO)));
    }

    @Test
    void minAndMaxKeepFirstOccurrenceOnTies() {
        BigDecimal firstMin = new BigDecimal("1.00");
        BigDecimal firstMax = new BigDecimal("2.0");
        assertSame(firstMin, BigDecimalUtils.min(firstMin, new BigDecimal("1.0"), TWO));
        assertSame(firstMax, BigDecimalUtils.max(ONE, firstMax, new BigDecimal("2.00")));
    }

    @Test
    void minAndMaxRejectEmptyOrNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> BigDecimalUtils.min(List.of()));
        assertThrows(IllegalArgumentException.class, () -> BigDecimalUtils.max(List.of()));
        assertThrows(IllegalArgumentException.class, () -> BigDecimalUtils.min());
        assertThrows(IllegalArgumentException.class, () -> BigDecimalUtils.max());
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.min((Collection<BigDecimal>) null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.max((Collection<BigDecimal>) null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.min(Arrays.asList(ONE, null)));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.max(Arrays.asList(ONE, null)));
    }

    @Test
    void isZeroChecksNumericZero() {
        assertTrue(BigDecimalUtils.isZero(BigDecimal.ZERO));
        assertTrue(BigDecimalUtils.isZero(new BigDecimal("-0.00")));
        assertFalse(BigDecimalUtils.isZero(ONE));
        assertFalse(BigDecimalUtils.isZero(ONE.negate()));
    }

    @Test
    void isPositiveAndIsNegativeCheckSign() {
        assertTrue(BigDecimalUtils.isPositive(ONE));
        assertFalse(BigDecimalUtils.isPositive(BigDecimal.ZERO));
        assertFalse(BigDecimalUtils.isPositive(ONE.negate()));
        assertTrue(BigDecimalUtils.isNegative(ONE.negate()));
        assertFalse(BigDecimalUtils.isNegative(BigDecimal.ZERO));
        assertFalse(BigDecimalUtils.isNegative(ONE));
    }

    @Test
    void signMethodsRejectNull() {
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.isZero(null));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.isPositive(null));
        assertThrows(NullPointerException.class, () -> BigDecimalUtils.isNegative(null));
    }

    @Test
    void sumAddsCollectionAndVarargs() {
        assertNumericEquals(new BigDecimal("6.5"),
                BigDecimalUtils.sum(List.of(ONE, TWO, THREE, new BigDecimal("0.5"))));
        assertNumericEquals(new BigDecimal("1.5"), BigDecimalUtils.sum(ONE, new BigDecimal("0.5")));
        assertSame(BigDecimal.ZERO, BigDecimalUtils.sum(List.of()));
        assertSame(BigDecimal.ZERO, BigDecimalUtils.sum());
    }

    @Test
    void mulMultipliesCollectionAndVarargs() {
        assertNumericEquals(new BigDecimal("6"), BigDecimalUtils.mul(List.of(ONE, TWO, THREE)));
        assertNumericEquals(new BigDecimal("0.5"), BigDecimalUtils.mul(ONE, new BigDecimal("0.5")));
        assertNumericEquals(BigDecimal.ZERO, BigDecimalUtils.mul(ONE, BigDecimal.ZERO));
        assertSame(BigDecimal.ONE, BigDecimalUtils.mul(List.of()));
        assertSame(BigDecimal.ONE, BigDecimalUtils.mul());
    }

    @Test
    void sumAndMulRejectNullInputs() {
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.sum((Collection<BigDecimal>) null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.sum((BigDecimal[]) null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.sum(Arrays.asList(ONE, null)));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.mul((Collection<BigDecimal>) null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.mul((BigDecimal[]) null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalUtils.mul(Arrays.asList(ONE, null)));
    }

    private static void assertNumericEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
