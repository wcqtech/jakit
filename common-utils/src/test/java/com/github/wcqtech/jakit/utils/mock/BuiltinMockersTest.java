package com.github.wcqtech.jakit.utils.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.wcqtech.jakit.utils.mock.mocker.BigDecimalMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.BigIntegerMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.BooleanMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.ByteMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.CharacterMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.DoubleMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.FloatMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.IntegerMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.LocalDateTimeMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.LongMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.PositiveBigDecimalMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.ShortMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.StringMocker;

class BuiltinMockersTest {

    private static final BigDecimal DECIMAL_MAX = BigDecimal.valueOf(999_999L, 2);
    private static final BigDecimal DECIMAL_MIN = BigDecimal.valueOf(-999_999L, 2);

    @Test
    void stringValuesAreShortReadableAndDeterministic() {
        StringMocker mocker = new StringMocker();
        MockContext context = context("alpha");
        String first = mocker.mock(context);
        String second = mocker.mock(context);
        assertEquals(first, second);
        assertTrue(first.startsWith("mock-"));
        assertTrue(first.length() <= StringMocker.MAX_LENGTH);
        assertTrue(first.length() >= "mock-".length() + 1);
    }

    @Test
    void stringValuesVaryAcrossSlots() {
        StringMocker mocker = new StringMocker();
        assertNotEquals(mocker.mock(context("alpha")), mocker.mock(context("beta")));
        assertNotEquals(mocker.mock(context("alpha")), mocker.mock(streamElement(0)));
    }

    @Test
    void integerFamilyRespectsDocumentedRanges() {
        assertTrue(inRange(new IntegerMocker().mock(context("a")), 0, 999_999));
        assertTrue(inRange(new LongMocker().mock(context("a")), 0L, 999_999_999_999L));
        assertTrue(inRange(new ShortMocker().mock(context("a")), 0, 999));
        assertTrue(inRange(new ByteMocker().mock(context("a")), 0, 99));
        for (int i = 0; i < 10; i++) {
            MockContext slot = streamElement(i);
            assertTrue(inRange(new IntegerMocker().mock(slot), 0, 999_999));
            assertTrue(inRange(new LongMocker().mock(slot), 0L, 999_999_999_999L));
            assertTrue(inRange(new ShortMocker().mock(slot), 0, 999));
            assertTrue(inRange(new ByteMocker().mock(slot), 0, 99));
        }
    }

    @Test
    void booleanAndCharacterProduceExpectedDomainValues() {
        BooleanMocker booleanMocker = new BooleanMocker();
        for (int i = 0; i < 10; i++) {
            assertInstanceOf(Boolean.class, booleanMocker.mock(streamElement(i)));
        }
        CharacterMocker characterMocker = new CharacterMocker();
        for (int i = 0; i < 10; i++) {
            char value = characterMocker.mock(streamElement(i));
            assertTrue(value >= 'A' && value <= 'Z', "unexpected character: " + value);
        }
    }

    @Test
    void decimalValuesRespectScaleAndRange() {
        BigDecimalMocker mocker = new BigDecimalMocker();
        for (int i = 0; i < 10; i++) {
            BigDecimal value = mocker.mock(streamElement(i));
            assertEquals(2, value.scale());
            assertTrue(value.compareTo(DECIMAL_MIN) >= 0, "below range: " + value);
            assertTrue(value.compareTo(DECIMAL_MAX) <= 0, "above range: " + value);
        }
        DoubleMocker doubleMocker = new DoubleMocker();
        FloatMocker floatMocker = new FloatMocker();
        for (int i = 0; i < 10; i++) {
            double doubleValue = doubleMocker.mock(streamElement(i));
            assertTrue(doubleValue >= -9999.99 && doubleValue <= 9999.99);
            float floatValue = floatMocker.mock(streamElement(i));
            assertTrue(floatValue >= -9999.99f && floatValue <= 9999.99f);
        }
    }

    @Test
    void positiveBigDecimalIsStrictlyPositiveWithinRange() {
        PositiveBigDecimalMocker mocker = new PositiveBigDecimalMocker();
        for (int i = 0; i < 10; i++) {
            BigDecimal value = mocker.mock(streamElement(i));
            assertEquals(2, value.scale());
            assertTrue(value.compareTo(BigDecimal.ZERO) > 0, "not positive: " + value);
            assertTrue(value.compareTo(DECIMAL_MAX) <= 0, "above range: " + value);
        }
    }

    @Test
    void bigIntegerValuesArePositiveAndBounded() {
        BigIntegerMocker mocker = new BigIntegerMocker();
        for (int i = 0; i < 10; i++) {
            BigInteger value = mocker.mock(streamElement(i));
            assertTrue(value.signum() > 0, "not positive: " + value);
            assertTrue(value.compareTo(BigInteger.valueOf(1_000_000)) <= 0, "too large: " + value);
        }
    }

    @Test
    void localDateTimeFallsInsideTheDocumentedWindow() {
        LocalDateTimeMocker mocker = new LocalDateTimeMocker();
        LocalDateTime windowEnd = LocalDateTimeMocker.START.plusSeconds(LocalDateTimeMocker.WINDOW_SECONDS);
        for (int i = 0; i < 10; i++) {
            LocalDateTime value = mocker.mock(streamElement(i));
            assertTrue(!value.isBefore(LocalDateTimeMocker.START), "before window: " + value);
            assertTrue(value.isBefore(windowEnd), "after window: " + value);
        }
    }

    @Test
    void everyBuiltInMockerIsDeterministic() {
        List<Mocker<?>> mockers = List.of(new StringMocker(), new IntegerMocker(), new LongMocker(),
                new ShortMocker(), new ByteMocker(), new BooleanMocker(), new CharacterMocker(),
                new FloatMocker(), new DoubleMocker(), new BigDecimalMocker(), new BigIntegerMocker(),
                new PositiveBigDecimalMocker(), new LocalDateTimeMocker());
        for (Mocker<?> mocker : mockers) {
            Object first = mocker.mock(context("same"));
            Object second = mocker.mock(context("same"));
            assertEquals(first, second, "mocker not deterministic: " + mocker.getClass().getName());
        }
    }

    @Test
    void everyBuiltInMockerVariesAcrossSlots() {
        List<Mocker<?>> mockers = List.of(new StringMocker(), new IntegerMocker(), new LongMocker(),
                new ShortMocker(), new ByteMocker(), new CharacterMocker(), new FloatMocker(),
                new DoubleMocker(), new BigDecimalMocker(), new BigIntegerMocker(),
                new PositiveBigDecimalMocker(), new LocalDateTimeMocker());
        for (Mocker<?> mocker : mockers) {
            java.util.Set<Object> values = new java.util.HashSet<>();
            for (int i = 0; i < 8; i++) {
                values.add(mocker.mock(streamElement(i)));
            }
            assertTrue(values.size() >= 2,
                    "no variation across slots: " + mocker.getClass().getName() + " -> " + values);
        }
    }

    private static MockContext context(String path) {
        return new MockContext(BuiltinMockersTest.class, path, path, -1);
    }

    private static MockContext streamElement(int position) {
        return new MockContext(BuiltinMockersTest.class, "", null, position);
    }

    private static boolean inRange(Number value, long min, long max) {
        long number = value.longValue();
        return number >= min && number <= max;
    }
}
