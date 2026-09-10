package com.github.wcqtech.jakit.dbucket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BucketSpecTest {

    private static final BigDecimal TEN = new BigDecimal("10");
    private static final BigDecimal ONE_POINT_FIVE = new BigDecimal("1.5");

    @Test
    void defaultsToFullBucket() {
        BucketSpec spec = BucketSpec.of("orders", "checkout", TEN, ONE_POINT_FIVE);

        assertEquals(BucketSpec.InitialState.FULL, spec.initialState());
        assertEquals(0, spec.initialTokens().compareTo(TEN));
    }

    @Test
    void emptyBucketStartsWithZeroTokens() {
        BucketSpec spec = BucketSpec.of("orders", "checkout", TEN, ONE_POINT_FIVE,
                BucketSpec.InitialState.EMPTY);

        assertEquals(0, spec.initialTokens().compareTo(BigDecimal.ZERO));
    }

    @Test
    void canonicalizesTrailingZerosWithoutExponentForm() {
        BucketSpec spec = BucketSpec.of("ns", "b", new BigDecimal("10.0000000"),
                new BigDecimal("1.5000000"));

        assertEquals("10", spec.capacity().toPlainString());
        assertEquals("1.5", spec.rate().toPlainString());
    }

    @Test
    void allowsZeroRateForManuallyFilledBuckets() {
        BucketSpec spec = BucketSpec.of("ns", "b", TEN, BigDecimal.ZERO);

        assertEquals(0, spec.rate().compareTo(BigDecimal.ZERO));
    }

    @Test
    void acceptsBoundaryLengthsAndValues() {
        String namespace = "n".repeat(BucketSpec.NAMESPACE_MAX_LENGTH);
        String name = "x".repeat(BucketSpec.NAME_MAX_LENGTH);
        BigDecimal maxCapacity = new BigDecimal("99999999999999");

        BucketSpec spec = BucketSpec.of(namespace, name, maxCapacity, new BigDecimal("0.000001"));

        assertEquals(14, spec.capacity().precision());
        assertEquals(6, spec.rate().scale());
    }

    @Test
    void rejectsBlankNamespace() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("  ", "b", TEN, ONE_POINT_FIVE));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", null, TEN, ONE_POINT_FIVE));
    }

    @Test
    void rejectsOverlongNamespace() {
        String namespace = "n".repeat(BucketSpec.NAMESPACE_MAX_LENGTH + 1);

        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of(namespace, "b", TEN, ONE_POINT_FIVE));
    }

    @Test
    void rejectsOverlongName() {
        String name = "x".repeat(BucketSpec.NAME_MAX_LENGTH + 1);

        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", name, TEN, ONE_POINT_FIVE));
    }

    @Test
    void rejectsZeroOrNegativeCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", "b", BigDecimal.ZERO, ONE_POINT_FIVE));
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", "b", new BigDecimal("-1"), ONE_POINT_FIVE));
    }

    @Test
    void rejectsNegativeRate() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", "b", TEN, new BigDecimal("-0.5")));
    }

    @Test
    void rejectsScaleBeyondStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", "b", new BigDecimal("1.0000001"), ONE_POINT_FIVE));
    }

    @Test
    void rejectsTooManyIntegerDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", "b", new BigDecimal("100000000000000"), ONE_POINT_FIVE));
    }

    @Test
    void rejectsNullCapacityAndRate() {
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", "b", null, ONE_POINT_FIVE));
        assertThrows(IllegalArgumentException.class,
                () -> BucketSpec.of("ns", "b", TEN, null));
    }

    @Test
    void rejectsNullInitialState() {
        assertThrows(IllegalArgumentException.class,
                () -> new BucketSpec("ns", "b", TEN, ONE_POINT_FIVE, null));
    }
}
