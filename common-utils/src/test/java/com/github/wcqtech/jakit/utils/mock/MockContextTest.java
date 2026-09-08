package com.github.wcqtech.jakit.utils.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MockContextTest {

    @Test
    void identicalContextsProduceIdenticalSeeds() {
        MockContext first = new MockContext(String.class, "order.items[1].price", "price", 1);
        MockContext second = new MockContext(String.class, "order.items[1].price", "price", 1);
        assertEquals(first.getSeed(), second.getSeed());
        assertEquals(first.getRootType(), second.getRootType());
        assertEquals(first.getPath(), second.getPath());
        assertEquals(first.getFieldName(), second.getFieldName());
        assertEquals(first.getPosition(), second.getPosition());
    }

    @Test
    void twoRootCallsOfSameStructureShareTheSeed() {
        MockContext first = new MockContext(Order.class, "", null, -1);
        MockContext second = new MockContext(Order.class, "", null, -1);
        assertEquals(first.getSeed(), second.getSeed());
    }

    @Test
    void differentPathsProduceDifferentSeeds() {
        MockContext alpha = new MockContext(String.class, "alpha", "alpha", -1);
        MockContext beta = new MockContext(String.class, "beta", "beta", -1);
        assertNotEquals(alpha.getSeed(), beta.getSeed());
    }

    @Test
    void streamPositionsProduceDifferentSeeds() {
        long first = new MockContext(Order.class, "", null, 0).getSeed();
        long second = new MockContext(Order.class, "", null, 1).getSeed();
        long third = new MockContext(Order.class, "", null, 2).getSeed();
        assertNotEquals(first, second);
        assertNotEquals(second, third);
        assertNotEquals(first, third);
    }

    @Test
    void rootSlotDiffersFromStreamElementSlot() {
        long root = new MockContext(Order.class, "", null, -1).getSeed();
        long firstElement = new MockContext(Order.class, "", null, 0).getSeed();
        assertNotEquals(root, firstElement);
    }

    @Test
    void nullRootTypeIsRejected() {
        assertThrows(NullPointerException.class, () -> new MockContext(null, "", null, -1));
    }

    @Test
    void nullPathIsRejected() {
        assertThrows(NullPointerException.class, () -> new MockContext(Order.class, null, null, -1));
    }

    @Test
    void positionBelowMinusOneIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new MockContext(Order.class, "", null, -2));
        assertTrue(error.getMessage().contains("-2"));
    }

    @Test
    void nullableFieldNameIsAllowed() {
        MockContext context = new MockContext(Order.class, "items[0]", null, 0);
        assertNull(context.getFieldName());
        assertEquals(0, context.getPosition());
        assertEquals("items[0]", context.getPath());
    }

    private static final class Order {
    }
}
