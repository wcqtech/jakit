package com.github.wcqtech.jakit.utils.amount;

import com.github.wcqtech.jakit.utils.number.BigDecimalFormatUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility methods for Chinese monetary amounts.
 *
 * Digit grouping uses the Chinese locale's three-digit grouping; for
 * example {@code 1234567.891} becomes {@code "1,234,567.891"}.
 * Uppercase conversion follows the financial writing rules used for official
 * Chinese amounts; for example {@code 123.45} becomes
 * {@code "壹佰贰拾叁元肆角伍分"}.
 */
public final class ChineseAmountUtils {

    private static final char[] DIGITS =
            {'零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'};
    private static final String[] UNITS = {"", "拾", "佰", "仟"};
    private static final String[] SECTION_UNITS =
            {"", "万", "亿", "兆", "京", "垓", "秭", "穰", "沟", "涧", "正", "载"};
    private static final String ZERO = "零";
    private static final String YUAN = "元";
    private static final String JIAO = "角";
    private static final String FEN = "分";
    private static final String WHOLE = "整";
    private static final String NEGATIVE = "负";
    private static final String RMB_SYMBOL = "￥";

    private ChineseAmountUtils() {
    }

    /**
     * Groups a value using Chinese digit grouping.
     *
     * This is the Chinese-locale convenience wrapper for
     * {@link BigDecimalFormatUtils#digitGrouping(BigDecimal, Locale)}; for
     * example {@code 1234567.891} becomes {@code "1,234,567.891"}.
     *
     * @param value the value to format; must not be null
     * @return the grouped value
     * @throws NullPointerException if {@code value} is null
     */
    public static String digitGrouping(BigDecimal value) {
        return BigDecimalFormatUtils.digitGrouping(value, Locale.CHINA);
    }

    /**
     * Formats an amount with the RMB symbol and Chinese digit grouping.
     *
     * The amount is rounded to two fraction digits with
     * {@link RoundingMode#HALF_UP}; for example {@code 1234.5} becomes
     * {@code "￥1,234.50"} and {@code -1234.5} becomes
     * {@code "-￥1,234.50"}.
     *
     * @param amount the amount to format; must not be null
     * @return the RMB-formatted amount
     * @throws NullPointerException if {@code amount} is null
     */
    public static String formatRmb(BigDecimal amount) {
        return formatRmb(amount, RoundingMode.HALF_UP);
    }

    /**
     * Formats an amount with the RMB symbol and Chinese digit grouping using
     * the given rounding mode.
     *
     * The amount is rounded to two fraction digits; for example
     * {@code 1234.5} becomes {@code "￥1,234.50"} and {@code -1234.5}
     * becomes {@code "-￥1,234.50"}.
     *
     * @param amount the amount to format; must not be null
     * @param roundingMode the rounding mode applied when the amount has more
     *                     than two fraction digits; must not be null
     * @return the RMB-formatted amount
     * @throws NullPointerException if {@code amount} or {@code roundingMode}
     *                              is null
     */
    public static String formatRmb(BigDecimal amount, RoundingMode roundingMode) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(roundingMode, "roundingMode must not be null");
        BigDecimal rounded = amount.setScale(2, roundingMode);
        String sign = rounded.signum() < 0 ? "-" : "";
        return sign + RMB_SYMBOL + digitGrouping(rounded.abs());
    }

    /**
     * Converts an amount to Chinese uppercase with two fraction digits and
     * {@link RoundingMode#HALF_UP} rounding.
     *
     * The result follows official financial writing rules: for example
     * {@code 123.45} becomes {@code "壹佰贰拾叁元肆角伍分"},
     * {@code 10.50} becomes {@code "壹拾元伍角整"} and
     * {@code 100000000.05} becomes {@code "壹亿元零伍分"}. Negative amounts
     * are prefixed with {@code 负}.
     *
     * @param amount the amount to convert; must not be null
     * @return the Chinese uppercase amount
     * @throws NullPointerException if {@code amount} is null
     */
    public static String toRMBUppercase(BigDecimal amount) {
        return toRMBUppercase(amount, RoundingMode.HALF_UP);
    }

    /**
     * Converts an amount to Chinese uppercase with two fraction digits and
     * the given rounding mode.
     *
     * The result follows official financial writing rules: for example
     * {@code 123.45} becomes {@code "壹佰贰拾叁元肆角伍分"},
     * {@code 10.50} becomes {@code "壹拾元伍角整"} and
     * {@code 100000000.05} becomes {@code "壹亿元零伍分"}. Negative amounts
     * are prefixed with {@code 负}.
     *
     * @param amount the amount to convert; must not be null
     * @param roundingMode the rounding mode applied when the amount has more
     *                     than two fraction digits; must not be null
     * @return the Chinese uppercase amount
     * @throws NullPointerException if {@code amount} or {@code roundingMode}
     *                              is null
     */
    public static String toRMBUppercase(BigDecimal amount, RoundingMode roundingMode) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(roundingMode, "roundingMode must not be null");
        BigDecimal rounded = amount.setScale(2, roundingMode);
        if (rounded.signum() == 0) {
            return ZERO + YUAN + WHOLE;
        }

        boolean negative = rounded.signum() < 0;
        String plain = rounded.abs().toPlainString();
        int dot = plain.indexOf('.');
        String integerPart = dot >= 0 ? plain.substring(0, dot) : plain;
        String fractionPart = dot >= 0 ? plain.substring(dot + 1) : "00";
        int jiao = fractionPart.charAt(0) - '0';
        int fen = fractionPart.charAt(1) - '0';

        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append(NEGATIVE);
        }
        result.append(integerToChinese(integerPart));
        result.append(YUAN);
        if (jiao == 0 && fen > 0) {
            result.append(ZERO);
        }
        if (jiao > 0) {
            result.append(DIGITS[jiao]).append(JIAO);
        }
        if (fen > 0) {
            result.append(DIGITS[fen]).append(FEN);
        }
        if (fen == 0) {
            result.append(WHOLE);
        }
        return result.toString();
    }

    private static String integerToChinese(String digits) {
        if (digits.equals("0")) {
            return ZERO;
        }
        StringBuilder result = new StringBuilder();
        boolean zeroPending = false;
        int length = digits.length();
        int groupCount = (length + 3) / 4;
        for (int sectionIndex = groupCount - 1; sectionIndex >= 0; sectionIndex--) {
            int groupStart = Math.max(0, length - (sectionIndex + 1) * 4);
            int groupEnd = length - sectionIndex * 4;
            String block = digits.substring(groupStart, groupEnd);
            if (isAllZero(block)) {
                zeroPending = true;
                continue;
            }
            if (result.length() > 0 && (zeroPending || Integer.parseInt(block) < 1000)) {
                result.append(ZERO);
            }
            zeroPending = false;
            result.append(readBlock(block));
            result.append(sectionUnit(sectionIndex));
        }
        return result.toString();
    }

    private static String readBlock(String block) {
        StringBuilder result = new StringBuilder();
        boolean zeroPending = false;
        int length = block.length();
        for (int i = 0; i < length; i++) {
            int digit = block.charAt(i) - '0';
            int place = length - i - 1;
            if (digit == 0) {
                if (result.length() > 0) {
                    zeroPending = true;
                }
            } else {
                if (zeroPending) {
                    result.append(ZERO);
                }
                zeroPending = false;
                result.append(DIGITS[digit]);
                if (place > 0) {
                    result.append(UNITS[place]);
                }
            }
        }
        return result.toString();
    }

    private static boolean isAllZero(String block) {
        for (int i = 0; i < block.length(); i++) {
            if (block.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private static String sectionUnit(int sectionIndex) {
        return sectionIndex < SECTION_UNITS.length ? SECTION_UNITS[sectionIndex] : "";
    }
}
