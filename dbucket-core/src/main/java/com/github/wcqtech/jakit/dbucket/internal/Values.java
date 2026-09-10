package com.github.wcqtech.jakit.dbucket.internal;

import java.math.BigDecimal;

/**
 * Validation helpers shared by the SPI value objects and the JDBC storage implementation.
 *
 * <p>This package is not part of the public API: types here may change between minor releases.
 */
public final class Values {

    /** Fractional digits of the stored token columns ({@code NUMERIC(20,6)} / {@code DECIMAL(20,6)}). */
    public static final int TOKEN_SCALE = 6;

    /** Whole-token digits available in {@code NUMERIC(20,6)} / {@code DECIMAL(20,6)}. */
    public static final int TOKEN_INTEGER_DIGITS = 14;

    private Values() {
    }

    /**
     * Requires non-blank text no longer than {@code maxLength} characters.
     *
     * @return the value, for convenient assignment in compact record constructors
     */
    public static String requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " length must be <= " + maxLength + " but was " + value.length());
        }
        return value;
    }

    /**
     * Requires a non-negative (or positive when {@code allowZero} is {@code false}) decimal that can
     * be stored losslessly in {@code NUMERIC(20,6)}.
     *
     * <p>The returned value is canonical: trailing zeros are removed and negative scales are
     * flattened to scale {@code 0}, so {@code 10.0000000} becomes {@code 10} and never {@code 1E+1}.
     *
     * @return the canonicalized value, for convenient assignment in compact record constructors
     */
    public static BigDecimal requireDecimal(String field, BigDecimal value, boolean allowZero) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.signum() < 0 || (!allowZero && normalized.signum() == 0)) {
            throw new IllegalArgumentException(field + " must be "
                    + (allowZero ? ">= 0" : "> 0") + " but was " + value.toPlainString());
        }
        if (normalized.scale() > TOKEN_SCALE) {
            throw new IllegalArgumentException(field + " scale must be <= " + TOKEN_SCALE
                    + " but was " + value.toPlainString());
        }
        if (normalized.precision() - normalized.scale() > TOKEN_INTEGER_DIGITS) {
            throw new IllegalArgumentException(field + " integer digits must be <= "
                    + TOKEN_INTEGER_DIGITS + " but was " + value.toPlainString());
        }
        return normalized;
    }
}
