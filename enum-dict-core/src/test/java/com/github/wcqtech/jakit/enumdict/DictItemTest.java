package com.github.wcqtech.jakit.enumdict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DictItemTest {

    @Test
    void threeArgConstructorLeavesI18nKeyNull() {
        DictItem item = new DictItem("order_status", "1", "Pending");

        assertNull(item.i18nKey());
        assertEquals("Pending", item.value());
    }

    @Test
    void fourArgConstructorKeepsI18nKey() {
        DictItem item = new DictItem("order_status", "1", "Pending", "order.pending");

        assertEquals("order.pending", item.i18nKey());
    }

    @Test
    void normalizesBlankI18nKeyToNull() {
        assertNull(new DictItem("order_status", "1", "Pending", null).i18nKey());
        assertNull(new DictItem("order_status", "1", "Pending", "").i18nKey());
        assertNull(new DictItem("order_status", "1", "Pending", "   ").i18nKey());
    }

    @Test
    void rejectsNullCoreComponents() {
        assertThrows(NullPointerException.class, () -> new DictItem(null, "1", "Pending"));
        assertThrows(NullPointerException.class, () -> new DictItem("order_status", null, "Pending"));
        assertThrows(NullPointerException.class, () -> new DictItem("order_status", "1", null));
    }
}
