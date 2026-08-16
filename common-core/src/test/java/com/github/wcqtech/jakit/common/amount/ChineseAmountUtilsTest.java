package com.github.wcqtech.jakit.common.amount;

import com.github.wcqtech.jakit.common.number.BigDecimalFormatUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChineseAmountUtilsTest {

    @Test
    void groupsWithChineseLocale() {
        assertEquals("1,234,567.891",
                ChineseAmountUtils.digitGrouping(new BigDecimal("1234567.891")));
        assertEquals("123,456,789",
                ChineseAmountUtils.digitGrouping(new BigDecimal("123456789")));
        assertEquals("123,456,789.01",
                ChineseAmountUtils.digitGrouping(new BigDecimal("123456789.01")));
        assertEquals("1,234,567,891.01",
                ChineseAmountUtils.digitGrouping(new BigDecimal("1234567891.01")));
    }

    @Test
    void groupingHandlesNegativeAndSmallValues() {
        assertEquals("-1,234,567.891",
                ChineseAmountUtils.digitGrouping(new BigDecimal("-1234567.891")));
        assertEquals("123.45",
                ChineseAmountUtils.digitGrouping(new BigDecimal("123.45")));
        assertEquals("0", ChineseAmountUtils.digitGrouping(BigDecimal.ZERO));
    }

    @Test
    void groupingHandlesScientificNotationAndTrailingZeros() {
        assertEquals("10,000,000,000",
                ChineseAmountUtils.digitGrouping(new BigDecimal("1E+10")));
        assertEquals("1.2300",
                ChineseAmountUtils.digitGrouping(new BigDecimal("1.2300")));
    }

    @Test
    void groupingMatchesBigDecimalFormatUtilsWithChineseLocale() {
        BigDecimal value = new BigDecimal("1234567.891");
        assertEquals(BigDecimalFormatUtils.digitGrouping(value, Locale.CHINA),
                ChineseAmountUtils.digitGrouping(value));
    }

    @Test
    void groupingRejectsInvalidArguments() {
        assertThrows(NullPointerException.class,
                () -> ChineseAmountUtils.digitGrouping(null));
    }

    @Test
    void formatRmbFormatsWithSymbolAndGrouping() {
        assertEquals("￥1,234.50",
                ChineseAmountUtils.formatRmb(new BigDecimal("1234.5")));
        assertEquals("￥1,234,567.89",
                ChineseAmountUtils.formatRmb(new BigDecimal("1234567.891")));
        assertEquals("￥0.00", ChineseAmountUtils.formatRmb(BigDecimal.ZERO));
        assertEquals("￥0.05", ChineseAmountUtils.formatRmb(new BigDecimal("0.05")));
    }

    @Test
    void formatRmbHandlesSignAndRounding() {
        assertEquals("-￥1,234.50",
                ChineseAmountUtils.formatRmb(new BigDecimal("-1234.5")));
        assertEquals("￥1.01", ChineseAmountUtils.formatRmb(new BigDecimal("1.009")));
        assertEquals("￥1.00",
                ChineseAmountUtils.formatRmb(new BigDecimal("1.009"), RoundingMode.DOWN));
    }

    @Test
    void formatRmbRejectsInvalidArguments() {
        assertThrows(NullPointerException.class,
                () -> ChineseAmountUtils.formatRmb(null));
        assertThrows(NullPointerException.class,
                () -> ChineseAmountUtils.formatRmb(BigDecimal.ONE, null));
    }

    @Test
    void uppercaseConvertsBasicAmounts() {
        assertEquals("零元整", ChineseAmountUtils.toRMBUppercase(BigDecimal.ZERO));
        assertEquals("壹佰贰拾叁元肆角伍分",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("123.45")));
        assertEquals("零元伍角整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("0.50")));
        assertEquals("零元零伍分",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("0.05")));
        assertEquals("壹拾元壹角整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("10.10")));
    }

    @Test
    void uppercaseHandlesSectionUnits() {
        assertEquals("壹亿元整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("100000000")));
        assertEquals("壹亿元零伍分",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("100000000.05")));
        assertEquals("壹亿贰仟叁佰肆拾伍万陆仟柒佰捌拾玖元壹角贰分",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("123456789.12")));
    }

    @Test
    void uppercaseInsertsZeroBetweenSections() {
        assertEquals("壹亿零叁万元整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("100030000")));
        assertEquals("壹亿零叁拾万元整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("100300000")));
        assertEquals("壹亿贰仟叁佰万零肆佰伍拾陆元整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("123000456")));
        assertEquals("壹仟亿零壹元整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("100000000001")));
        assertEquals("壹拾亿元整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("1000000000")));
    }

    @Test
    void uppercaseRoundsAndHandlesSign() {
        assertEquals("壹元零壹分",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("1.009")));
        assertEquals("壹元整",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("1.009"), RoundingMode.DOWN));
        assertEquals("负壹佰贰拾叁元肆角伍分",
                ChineseAmountUtils.toRMBUppercase(new BigDecimal("-123.45")));
    }

    @Test
    void uppercaseRejectsInvalidArguments() {
        assertThrows(NullPointerException.class,
                () -> ChineseAmountUtils.toRMBUppercase(null));
        assertThrows(NullPointerException.class,
                () -> ChineseAmountUtils.toRMBUppercase(BigDecimal.ONE, null));
    }
}
