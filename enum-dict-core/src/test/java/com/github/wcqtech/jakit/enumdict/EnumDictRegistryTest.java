package com.github.wcqtech.jakit.enumdict;

import com.github.wcqtech.jakit.enumdict.convert.DictField;
import com.github.wcqtech.jakit.enumdict.convert.MissingPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnumDictRegistryTest {

    @Test
    void returnsAllItemsGroupedByType() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Pending")));
        registry.register("pay_channel", List.of(new DictItem("pay_channel", "1", "WeChat")));

        Map<String, List<DictItem>> all = registry.itemsByType();

        assertEquals(2, all.size());
        assertEquals("Pending", all.get("order_status").get(0).value());
        assertEquals("WeChat", all.get("pay_channel").get(0).value());
        assertThrows(UnsupportedOperationException.class, () -> all.get("order_status").add(null));
    }

    @Test
    void returnsFlatListOfAllItems() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Pending")));
        registry.register("pay_channel", List.of(new DictItem("pay_channel", "1", "WeChat")));

        List<DictItem> all = registry.allItems();

        assertEquals(2, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add(null));
    }

    @Test
    void convertsWithConfiguredMissingPolicy() {
        EnumDictRegistry registry = new EnumDictRegistry(MissingPolicy.FAIL);
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Paid")));

        MissingKeyBean bean = new MissingKeyBean();

        assertThrows(IllegalStateException.class, () -> registry.convert(bean));
    }

    static class MissingKeyBean {
        @DictField(type = "order_status")
        String status = "999";
    }
}
