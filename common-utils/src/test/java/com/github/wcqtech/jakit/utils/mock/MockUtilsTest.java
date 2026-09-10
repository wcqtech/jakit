package com.github.wcqtech.jakit.utils.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.github.wcqtech.jakit.utils.mock.mocker.PositiveBigDecimalMocker;
import com.github.wcqtech.jakit.utils.mock.mocker.StringMocker;

class MockUtilsTest {

    // ------------------------------------------------------------------ roots

    @Test
    void valueRootsResolveThroughRegistry() {
        assertTrue(MockUtils.mock(String.class).startsWith("mock-"));
        assertInstanceOf(Integer.class, MockUtils.mock(int.class));
        assertTrue(MockUtils.mock(BigDecimal.class).scale() == 2);
        assertInstanceOf(BigInteger.class, MockUtils.mock(BigInteger.class));
        assertInstanceOf(LocalDateTime.class, MockUtils.mock(LocalDateTime.class));
    }

    @Test
    void enumRootYieldsFirstConstant() {
        assertEquals(Level.LOW, MockUtils.mock(Level.class));
        assertEquals(Level.LOW, MockUtils.mock(Level.class));
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(NullPointerException.class, () -> MockUtils.mock((Class<?>) null));
        assertThrows(NullPointerException.class, () -> MockUtils.multiMock((Class<?>) null));
        assertThrows(NullPointerException.class, () -> MockUtils.multiMock((Class<?>) null, 3));
    }

    @Test
    void recordRootIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MockUtils.mock(Price.class));
        assertTrue(error.getMessage().contains("record"), error.getMessage());
    }

    @Test
    void unregisteredJdkRootIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MockUtils.mock(UUID.class));
        assertTrue(error.getMessage().contains("without a registered Mocker"), error.getMessage());
    }

    // ------------------------------------------------------------- full dto

    @Test
    void fullDtoIsFilledAccordingToThePipeline() {
        Order order = MockUtils.mock(Order.class);

        assertTrue(order.orderNo.startsWith("mock-"));
        assertInstanceOf(Integer.class, order.count);
        assertInstanceOf(Long.class, order.id);
        assertInstanceOf(Boolean.class, order.active);
        assertInstanceOf(Character.class, order.code);
        assertEquals(2, order.amount.scale());
        assertInstanceOf(LocalDateTime.class, order.createdAt);
        assertEquals(Level.LOW, order.level);
        assertTrue(order.positive.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(order.inherited.startsWith("mock-"));

        assertEquals(MockUtils.DEFAULT_ELEMENT_COUNT, order.items.size());
        for (OrderItem item : order.items) {
            assertTrue(item.sku.startsWith("mock-"));
            assertInstanceOf(Integer.class, item.quantity);
        }
        assertEquals(MockUtils.DEFAULT_ELEMENT_COUNT, order.names.length);
        for (String name : order.names) {
            assertTrue(name.startsWith("mock-"));
        }
        assertEquals(MockUtils.DEFAULT_ELEMENT_COUNT, order.ints.length);
        assertEquals(MockUtils.DEFAULT_ELEMENT_COUNT, order.counts.size());
        for (Integer value : order.counts.values()) {
            assertInstanceOf(Integer.class, value);
        }
        assertTrue(!order.tags.isEmpty() && order.tags.size() <= MockUtils.DEFAULT_ELEMENT_COUNT);
    }

    @Test
    void mockIgnoreLeavesReferencesNullAndPrimitivesAtDefaults() {
        Order order = MockUtils.mock(Order.class);
        assertNull(order.ignored);
        assertEquals(0, order.ignoredCount);
        assertFalse(order.ignoredFlag);
    }

    @Test
    void staticAndTransientFieldsAreSkipped() {
        Order order = MockUtils.mock(Order.class);
        assertEquals("constant", Order.CONSTANT);
        assertNull(order.transientField);
    }

    // ------------------------------------------------------------- annotations

    @Test
    void mockWithOverridesTheDefaultRule() {
        Order order = MockUtils.mock(Order.class);
        assertEquals(2, order.positive.scale());
        assertTrue(order.positive.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void mockWithTypeMismatchFailsWithFieldPath() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MockUtils.mock(Mismatch.class));
        assertTrue(error.getMessage().contains("count"), error.getMessage());
        assertTrue(error.getMessage().contains("not assignable"), error.getMessage());
    }

    @Test
    void mockWithMockerWithoutNoArgConstructorFails() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MockUtils.mock(NoNoArgHost.class));
        assertTrue(error.getMessage().contains("no-arg constructor"), error.getMessage());
    }

    // ------------------------------------------------------------- unsupported structures

    @Test
    void finalInstanceFieldIsRejectedUnlessIgnored() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MockUtils.mock(HasFinal.class));
        assertTrue(error.getMessage().contains("final"), error.getMessage());

        HasFinalIgnored ignored = MockUtils.mock(HasFinalIgnored.class);
        assertEquals("tag", ignored.tag);
    }

    @Test
    void rawCollectionWithoutGenericArgumentIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MockUtils.mock(RawList.class));
        assertTrue(error.getMessage().contains("generic"), error.getMessage());
    }

    @Test
    void recordFieldIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MockUtils.mock(HasPrice.class));
        assertTrue(error.getMessage().contains("record"), error.getMessage());
    }

    @Test
    void interfaceAndAbstractFieldsAreRejected() {
        IllegalArgumentException interfaceError = assertThrows(IllegalArgumentException.class,
                () -> MockUtils.mock(HasSpi.class));
        assertTrue(interfaceError.getMessage().contains("interface"), interfaceError.getMessage());

        IllegalArgumentException abstractError = assertThrows(IllegalArgumentException.class,
                () -> MockUtils.mock(HasShape.class));
        assertTrue(abstractError.getMessage().contains("abstract"), abstractError.getMessage());
    }

    @Test
    void unregisteredJdkFieldIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MockUtils.mock(HasJdkField.class));
        assertTrue(error.getMessage().contains("without a registered Mocker"), error.getMessage());
    }

    // ------------------------------------------------------------- cycles

    @Test
    void selfReferenceIsNulled() {
        Node node = MockUtils.mock(Node.class);
        assertNull(node.next);
        assertNull(node.children);
        assertTrue(node.name.startsWith("mock-"));
    }

    @Test
    void mutualReferenceIsNulled() {
        ChainA a = MockUtils.mock(ChainA.class);
        assertNull(a.b.a);
    }

    // ------------------------------------------------------------- registry extension

    @Test
    void registeredMockerWinsOverRecursion() {
        BigDecimal fixed = new BigDecimal("42.00");
        Mockers.register(Quota.class, context -> {
            Quota quota = new Quota();
            quota.amount = fixed;
            return quota;
        });
        Quota quota = MockUtils.mock(Quota.class);
        assertEquals(fixed, quota.amount);
    }

    // ------------------------------------------------------------- determinism

    @Test
    void mockingTheSameStructureTwiceProducesEqualValues() {
        Order first = MockUtils.mock(Order.class);
        Order second = MockUtils.mock(Order.class);
        assertNotSame(first, second);
        assertDeepEquals(first, second, "order");
    }

    @Test
    void multiMockIsDeterministicWithPrefixProperty() {
        List<Order> three = MockUtils.multiMock(Order.class).limit(3).collect(Collectors.toList());
        List<Order> threeAgain = MockUtils.multiMock(Order.class).limit(3).collect(Collectors.toList());
        List<Order> five = MockUtils.multiMock(Order.class).limit(5).collect(Collectors.toList());
        assertEquals(3, three.size());
        assertDeepEquals(three, threeAgain, "multi[0..2]");
        assertDeepEquals(three, five.subList(0, 3), "prefix");
        assertFalse(deepEquals(three.get(0), three.get(1)));
    }

    @Test
    void mockFromMockerProducesOneDeterministicValue() {
        Mocker<String> mocker = context -> "unit-" + Math.floorMod(context.getSeed(), 3);
        String first = MockUtils.mock(mocker);
        String second = MockUtils.mock(mocker);
        assertEquals(first, second);
        assertTrue(first.startsWith("unit-"));
        assertTrue(first.length() >= "unit-".length());
    }

    @Test
    void mockFromMockerReceivesPlaceholderRootContext() {
        MockContext[] captured = new MockContext[1];
        MockUtils.mock(context -> {
            captured[0] = context;
            return "x";
        });
        assertEquals(Object.class, captured[0].getRootType());
        assertEquals("", captured[0].getPath());
        assertEquals(-1, captured[0].getPosition());
        assertNull(captured[0].getFieldName());
    }

    @Test
    void multiMockFromMockerIsDeterministicWithPrefixProperty() {
        Mocker<String> mocker = context -> Math.floorMod(context.getSeed(), 3) == 0 ? "元"
                : Math.floorMod(context.getSeed(), 3) == 1 ? "万元" : "亿元";
        List<String> three = MockUtils.multiMock(mocker, 3).collect(Collectors.toList());
        List<String> threeAgain = MockUtils.multiMock(mocker, 3).collect(Collectors.toList());
        List<String> five = MockUtils.multiMock(mocker, 5).collect(Collectors.toList());
        assertEquals(three, threeAgain);
        assertEquals(three, five.subList(0, 3));
        Set<String> distinct = new java.util.HashSet<>(MockUtils.multiMock(mocker, 8).collect(Collectors.toList()));
        assertTrue(distinct.size() >= 2, "values should vary across positions: " + distinct);
        for (String value : distinct) {
            assertTrue(Set.of("元", "万元", "亿元").contains(value), "unexpected value: " + value);
        }
    }

    @Test
    void multiMockFromMockerPassesPositionSeeds() {
        List<MockContext> seen = new java.util.ArrayList<>();
        MockUtils.multiMock(context -> {
            seen.add(context);
            return "x";
        }, 3).toList();
        assertEquals(3, seen.size());
        for (int i = 0; i < seen.size(); i++) {
            MockContext context = seen.get(i);
            assertEquals(Object.class, context.getRootType());
            assertEquals("", context.getPath());
            assertEquals(i, context.getPosition());
            assertNull(context.getFieldName());
        }
    }

    @Test
    void mockerBasedCallsValidateArguments() {
        assertThrows(NullPointerException.class, () -> MockUtils.mock((Mocker<String>) null));
        assertThrows(NullPointerException.class, () -> MockUtils.multiMock((Mocker<String>) null));
        assertThrows(NullPointerException.class, () -> MockUtils.multiMock((Mocker<String>) null, 3));
        assertEquals(0, MockUtils.multiMock((Mocker<String>) (context -> "x"), 0).count());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MockUtils.multiMock((Mocker<String>) (context -> "x"), -1));
        assertTrue(error.getMessage().contains("negative"), error.getMessage());
    }

    @Test
    void boundedMultiMockReturnsExactlyTheRequestedSize() {
        assertEquals(0, MockUtils.multiMock(Order.class, 0).count());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MockUtils.multiMock(Order.class, -1));
        assertTrue(error.getMessage().contains("negative"), error.getMessage());

        List<Order> three = MockUtils.multiMock(Order.class, 3).collect(Collectors.toList());
        List<Order> threeFromUnbounded = MockUtils.multiMock(Order.class).limit(3).collect(Collectors.toList());
        List<Order> five = MockUtils.multiMock(Order.class, 5).collect(Collectors.toList());
        assertDeepEquals(three, threeFromUnbounded, "bounded-vs-limit");
        assertDeepEquals(three, five.subList(0, 3), "bounded prefix");
    }

    @Test
    void parallelMockingMatchesSequentialMocking() {
        List<Order> sequential = IntStream.range(0, 32).mapToObj(i -> MockUtils.mock(Order.class))
                .collect(Collectors.toList());
        List<Order> parallel = IntStream.range(0, 32).parallel().mapToObj(i -> MockUtils.mock(Order.class))
                .collect(Collectors.toList());
        assertDeepEquals(sequential, parallel, "parallel");
    }

    // ------------------------------------------------------------------ fixtures

    private enum Level {
        LOW, HIGH
    }

    private static class Order extends BaseOrder {
        private long id;
        private boolean active;
        private Integer count;
        private Character code;
        private String orderNo;
        private BigDecimal amount;
        private LocalDateTime createdAt;
        private Level level;
        private List<OrderItem> items;
        private Map<String, Integer> counts;
        private Set<String> tags;
        private String[] names;
        private int[] ints;
        private transient String transientField;
        @MockIgnore
        private String ignored;
        @MockIgnore
        private int ignoredCount;
        @MockIgnore
        private boolean ignoredFlag;
        @MockWith(PositiveBigDecimalMocker.class)
        private BigDecimal positive;
        private static final String CONSTANT = "constant";
    }

    private static class BaseOrder {
        String inherited;
    }

    private static class OrderItem {
        private String sku;
        private Integer quantity;
    }

    private static class Mismatch {
        @MockWith(StringMocker.class)
        private Integer count;
    }

    private static class NoNoArgMocker implements Mocker<String> {
        private NoNoArgMocker(String unused) {
        }

        @Override
        public String mock(MockContext context) {
            return "x";
        }
    }

    private static class NoNoArgHost {
        @MockWith(NoNoArgMocker.class)
        private String value;
    }

    private static class HasFinal {
        private final String tag = "tag";
    }

    private static class HasFinalIgnored {
        @MockIgnore
        private final String tag = "tag";
    }

    private static class RawList {
        @SuppressWarnings("rawtypes")
        private List raw;
    }

    private record Price(BigDecimal amount) {
    }

    private static class HasPrice {
        private Price price;
    }

    private interface Spi {
    }

    private static class HasSpi {
        private Spi spi;
    }

    private abstract static class Shape {
    }

    private static class HasShape {
        private Shape shape;
    }

    private static class HasJdkField {
        private LocalDate when;
    }

    private static class Node {
        private String name;
        private Node next;
        private List<Node> children;
    }

    private static class ChainA {
        private ChainB b;
    }

    private static class ChainB {
        private ChainA a;
    }

    private static class Quota {
        private BigDecimal amount;
    }

    // ------------------------------------------------------------------ deep equality helpers

    private static void assertDeepEquals(Object expected, Object actual, String where) {
        assertTrue(deepEquals(expected, actual), "expected and actual differ at " + where + ": expected="
                + describe(expected) + ", actual=" + describe(actual));
    }

    private static boolean deepEquals(Object expected, Object actual) {
        if (expected == actual) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.getClass().isArray()) {
            if (!actual.getClass().isArray()) {
                return false;
            }
            int length = java.lang.reflect.Array.getLength(expected);
            if (java.lang.reflect.Array.getLength(actual) != length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (!deepEquals(java.lang.reflect.Array.get(expected, i), java.lang.reflect.Array.get(actual, i))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof Collection<?> expectedCollection) {
            if (!(actual instanceof Collection<?> actualCollection)) {
                return false;
            }
            if (expectedCollection.size() != actualCollection.size()) {
                return false;
            }
            java.util.Iterator<?> left = expectedCollection.iterator();
            java.util.Iterator<?> right = actualCollection.iterator();
            while (left.hasNext()) {
                if (!deepEquals(left.next(), right.next())) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                return false;
            }
            if (expectedMap.size() != actualMap.size()) {
                return false;
            }
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!actualMap.containsKey(entry.getKey())
                        || !deepEquals(entry.getValue(), actualMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.getClass() != actual.getClass()) {
            return false;
        }
        if (isValueType(expected)) {
            return expected.equals(actual);
        }
        for (Class<?> type = expected.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (!deepEquals(field.get(expected), field.get(actual))) {
                        return false;
                    }
                } catch (IllegalAccessException exception) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isValueType(Object value) {
        return value.getClass().isEnum() || value.getClass().getPackageName().startsWith("java.");
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(MockUtilsTest::describe).collect(Collectors.joining(", ", "[", "]"));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> describe(entry.getKey()) + "=" + describe(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        return String.valueOf(value);
    }
}
