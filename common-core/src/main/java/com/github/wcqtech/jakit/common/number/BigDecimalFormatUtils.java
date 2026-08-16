package com.github.wcqtech.jakit.common.number;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility methods for formatting {@link BigDecimal} values.
 *
 * Percent formatting distinguishes two input semantics. A ratio such as
 * {@code 0.15} is displayed as {@code "15%"} by scaling it to a percentage.
 * An already percentage value such as {@code 15} is displayed as
 * {@code "15%"} without any further scaling. Formatted percentages never use
 * thousands grouping.
 */
public final class BigDecimalFormatUtils {

    private BigDecimalFormatUtils() {
    }

    /**
     * Formats a ratio as a percentage.
     *
     * The ratio is scaled by one hundred before formatting; for example
     * {@code 0.15} becomes {@code "15%"}.
     *
     * @param ratio the value between {@code 0} and {@code 1} for typical
     *              percentages; must not be null
     * @param scale the number of fraction digits in the formatted percentage;
     *              must not be negative
     * @param roundingMode the rounding mode applied when the displayed value
     *                     has more digits than {@code scale}; must not be null
     * @return the formatted percentage
     * @throws NullPointerException if {@code ratio} or {@code roundingMode} is
     *                              null
     * @throws IllegalArgumentException if {@code scale} is negative
     */
    public static String ratioPercent(BigDecimal ratio, int scale, RoundingMode roundingMode) {
        Objects.requireNonNull(ratio, "ratio must not be null");
        return formatWithPercent(ratio, scale, roundingMode);
    }

    /**
     * Formats a ratio as a percentage with two fraction digits and
     * {@link RoundingMode#HALF_UP} rounding.
     *
     * Equivalent to {@link #ratioPercent(BigDecimal, int, RoundingMode)}
     * with scale {@code 2} and {@link RoundingMode#HALF_UP}.
     *
     * @param ratio the value between {@code 0} and {@code 1} for typical
     *              percentages; must not be null
     * @return the formatted percentage
     * @throws NullPointerException if {@code ratio} is null
     */
    public static String ratioPercent(BigDecimal ratio) {
        return ratioPercent(ratio, 2, RoundingMode.HALF_UP);
    }

    /**
     * Formats an already percentage value.
     *
     * The value is not scaled; for example {@code 15} becomes {@code "15%"}.
     *
     * @param percent the percentage value; must not be null
     * @param scale the number of fraction digits in the formatted percentage;
     *              must not be negative
     * @param roundingMode the rounding mode applied when the displayed value
     *                     has more digits than {@code scale}; must not be null
     * @return the formatted percentage
     * @throws NullPointerException if {@code percent} or {@code roundingMode}
     *                              is null
     * @throws IllegalArgumentException if {@code scale} is negative
     */
    public static String percent(BigDecimal percent, int scale, RoundingMode roundingMode) {
        Objects.requireNonNull(percent, "percent must not be null");
        return formatWithPercent(percent.movePointLeft(2), scale, roundingMode);
    }

    /**
     * Formats an already percentage value with two fraction digits and
     * {@link RoundingMode#HALF_UP} rounding.
     *
     * Equivalent to {@link #percent(BigDecimal, int, RoundingMode)} with
     * scale {@code 2} and {@link RoundingMode#HALF_UP}.
     *
     * @param percent the percentage value; must not be null
     * @return the formatted percentage
     * @throws NullPointerException if {@code percent} is null
     */
    public static String percent(BigDecimal percent) {
        return percent(percent, 2, RoundingMode.HALF_UP);
    }

    /**
     * Formats a value with digit grouping (thousands separators) using the
     * given locale's grouping and decimal separators.
     *
     * All fraction digits of {@code value} are preserved exactly without
     * rounding, and no trailing zeros are added; for example
     * {@code 1234567.891} becomes {@code "1,234,567.891"} for
     * {@link Locale#US} and {@code "1.234.567,891"} for
     * {@link Locale#GERMANY}. Locale-specific grouping sizes are honored; for
     * example an Indian locale groups {@code 123456789} as
     * {@code "12,34,56,789"}.
     *
     * @param value the value to format; must not be null
     * @param locale the locale whose grouping and decimal separators are
     *               used; must not be null
     * @return the formatted value
     * @throws NullPointerException if {@code value} or {@code locale} is null
     */
    public static String digitGrouping(BigDecimal value, Locale locale) {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(locale, "locale must not be null");
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setGroupingUsed(true);
        int fractionDigits = Math.max(value.scale(), 0);
        numberFormat.setMinimumFractionDigits(fractionDigits);
        numberFormat.setMaximumFractionDigits(fractionDigits);
        return numberFormat.format(value);
    }

    /**
     * Formats a value with digit grouping using the given
     * {@link DecimalFormat} pattern and the locale's symbols.
     *
     * The pattern controls grouping, fraction digits and rounding; for
     * example the pattern {@code "#,##0.00"} formats {@code 1234567.891} as
     * {@code "1,234,567.89"} for {@link Locale#US} and as
     * {@code "1.234.567,89"} for {@link Locale#GERMANY}.
     *
     * @param value the value to format; must not be null
     * @param pattern the {@link DecimalFormat} pattern; must not be null
     * @param locale the locale whose symbols are used; must not be null
     * @return the formatted value
     * @throws NullPointerException if {@code value}, {@code pattern} or
     *                              {@code locale} is null
     * @throws IllegalArgumentException if {@code pattern} is invalid
     */
    public static String digitGrouping(BigDecimal value, String pattern, Locale locale) {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(locale, "locale must not be null");
        DecimalFormat decimalFormat =
                new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
        return decimalFormat.format(value);
    }

    private static String formatWithPercent(BigDecimal ratio, int scale, RoundingMode roundingMode) {
        Objects.requireNonNull(roundingMode, "roundingMode must not be null");
        if (scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
        DecimalFormat decimalFormat =
                new DecimalFormat("0.00%", DecimalFormatSymbols.getInstance(Locale.ROOT));
        decimalFormat.setMinimumFractionDigits(scale);
        decimalFormat.setMaximumFractionDigits(scale);
        decimalFormat.setRoundingMode(roundingMode);
        return decimalFormat.format(ratio);
    }
}
