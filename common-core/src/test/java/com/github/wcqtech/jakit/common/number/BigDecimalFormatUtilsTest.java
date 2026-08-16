package com.github.wcqtech.jakit.common.number;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BigDecimalFormatUtilsTest {

    @Test
    void ratioToPercentage() {
        assertEquals("15%",
                BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.15"), 0, RoundingMode.HALF_UP));
        assertEquals("15.50%",
                BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.155"), 2, RoundingMode.HALF_UP));
        assertEquals("15.56%",
                BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.1556"), 2, RoundingMode.HALF_UP));
        assertEquals("-15%",
                BigDecimalFormatUtils.ratioPercent(new BigDecimal("-0.15"), 0, RoundingMode.HALF_UP));
        assertEquals("100%",
                BigDecimalFormatUtils.ratioPercent(BigDecimal.ONE, 0, RoundingMode.HALF_UP));
    }

    @Test
    void percentDoesNotScaleInput() {
        assertEquals("15%",
                BigDecimalFormatUtils.percent(new BigDecimal("15"), 0, RoundingMode.HALF_UP));
        assertEquals("15.50%",
                BigDecimalFormatUtils.percent(new BigDecimal("15.5"), 2, RoundingMode.HALF_UP));
        assertEquals("15.56%",
                BigDecimalFormatUtils.percent(new BigDecimal("15.556"), 2, RoundingMode.HALF_UP));
        assertEquals("100%",
                BigDecimalFormatUtils.percent(new BigDecimal("100"), 0, RoundingMode.HALF_UP));
    }

    @Test
    void ratioPercentDefaultsToTwoDigitsAndHalfUp() {
        assertEquals("15.50%", BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.155")));
        assertEquals("15.56%", BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.1556")));
    }

    @Test
    void percentDefaultsToTwoDigitsAndHalfUp() {
        assertEquals("15.50%", BigDecimalFormatUtils.percent(new BigDecimal("15.5")));
        assertEquals("15.56%", BigDecimalFormatUtils.percent(new BigDecimal("15.556")));
    }

    @Test
    void doesNotGroupLargePercentages() {
        assertEquals("1234.50%",
                BigDecimalFormatUtils.ratioPercent(new BigDecimal("12.345"), 2, RoundingMode.HALF_UP));
        assertEquals("1234.50%",
                BigDecimalFormatUtils.percent(new BigDecimal("1234.5"), 2, RoundingMode.HALF_UP));
    }

    @Test
    void ratioAndPercentProduceSameDisplayForEquivalentInputs() {
        assertEquals(BigDecimalFormatUtils.ratioPercent(new BigDecimal("0.15"), 2, RoundingMode.HALF_UP),
                BigDecimalFormatUtils.percent(new BigDecimal("15"), 2, RoundingMode.HALF_UP));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> BigDecimalFormatUtils.ratioPercent(BigDecimal.ONE, -1, RoundingMode.HALF_UP));
        assertThrows(IllegalArgumentException.class,
                () -> BigDecimalFormatUtils.percent(BigDecimal.ONE, -1, RoundingMode.HALF_UP));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.ratioPercent(null, 0, RoundingMode.HALF_UP));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.percent(null, 0, RoundingMode.HALF_UP));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.ratioPercent(BigDecimal.ONE, 0, null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.percent(BigDecimal.ONE, 0, null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.ratioPercent(null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.percent(null));
    }

    @Test
    void digitGroupingUsesLocaleSeparators() {
        assertEquals("1,234,567.891",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), Locale.US));
        assertEquals("1.234.567,891",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), Locale.GERMANY));
    }

    @Test
    void digitGroupingHandlesNegativeAndSmallValues() {
        assertEquals("-1,234,567.891",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("-1234567.891"), Locale.US));
        assertEquals("123.45",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("123.45"), Locale.US));
        assertEquals("0",
                BigDecimalFormatUtils.digitGrouping(BigDecimal.ZERO, Locale.US));
    }

    @Test
    void digitGroupingHonorsLocaleGroupingSize() {
        assertEquals("123,456,789",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("123456789"), new Locale("hi", "IN")));
    }

    @Test
    void digitGroupingHandlesScientificNotation() {
        assertEquals("10,000,000,000",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("1E+10"), Locale.US));
    }

    @Test
    void digitGroupingPreservesTrailingZeros() {
        assertEquals("1.2300",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("1.2300"), Locale.US));
    }

    @Test
    void digitGroupingWithPattern() {
        assertEquals("1,234,567.89",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), "#,##0.00", Locale.US));
        assertEquals("1.234.567,89",
                BigDecimalFormatUtils.digitGrouping(new BigDecimal("1234567.891"), "#,##0.00", Locale.GERMANY));
    }

    @Test
    void digitGroupingRejectsInvalidArguments() {
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.digitGrouping(null, Locale.US));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.digitGrouping(BigDecimal.ONE, null));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.digitGrouping(null, "#,##0.00", Locale.US));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.digitGrouping(BigDecimal.ONE, null, Locale.US));
        assertThrows(NullPointerException.class,
                () -> BigDecimalFormatUtils.digitGrouping(BigDecimal.ONE, "#,##0.00", null));
        assertThrows(IllegalArgumentException.class,
                () -> BigDecimalFormatUtils.digitGrouping(BigDecimal.ONE, "0.0.0", Locale.US));
    }
}
