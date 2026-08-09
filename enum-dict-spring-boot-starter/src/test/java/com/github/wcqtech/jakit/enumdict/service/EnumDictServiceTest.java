package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.convert.DictField;
import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumDictServiceTest {

    @Test
    void delegatesReadQueriesToRegistry() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Paid")));
        EnumDictService service = service(registry);

        assertTrue(service.contains("order_status", "1"));
        assertEquals("Paid", service.itemByKey("order_status", "1").orElseThrow().value());
        assertEquals(Optional.of("Paid"), service.valueByKey("order_status", "1"));
        assertEquals(Set.of("order_status"), service.types());
        assertEquals("Paid", service.itemMap("order_status").get("1").value());
    }

    @Test
    void supportsReverseLookupByValue() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(
                new DictItem("order_status", "1", "Pending"),
                new DictItem("order_status", "2", "Paid"),
                new DictItem("order_status", "3", "Paid")));
        EnumDictService service = service(registry);

        assertEquals(List.of("2", "3"), service.keysByValue("order_status", "Paid"));
        assertEquals(Optional.of("2"), service.keyByValue("order_status", "Paid"));
        assertEquals(2, service.itemsByValue("order_status", "Paid").size());
        assertEquals("2", service.itemByValue("order_status", "Paid").orElseThrow().key());
        assertEquals(Optional.empty(), service.itemByValue("order_status", "Missing"));
    }

    @Test
    void rejectsNullArguments() {
        EnumDictService service = service(new EnumDictRegistry());

        assertThrows(NullPointerException.class, () -> service.items(null));
        assertThrows(NullPointerException.class, () -> service.itemByKey(null, "1"));
        assertThrows(NullPointerException.class, () -> service.itemByKey("order_status", null));
        assertThrows(NullPointerException.class, () -> service.itemsByValue("order_status", null));
        assertThrows(NullPointerException.class, () -> service.itemByValue(null, "Paid"));
        assertThrows(NullPointerException.class, () -> service.valueByKey("order_status", null));
        assertThrows(NullPointerException.class, () -> service.keysByValue(null, "Paid"));
        assertThrows(NullPointerException.class, () -> service.keyByValue("order_status", null));
        assertThrows(NullPointerException.class, () -> service.itemMap(null));
        assertThrows(NullPointerException.class, () -> service.contains("order_status", null));
    }

    @Test
    void exposesFullDictionarySnapshots() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Pending")));
        registry.register("pay_channel", List.of(new DictItem("pay_channel", "1", "WeChat")));
        EnumDictService service = service(registry);

        assertEquals(2, service.itemsByType().size());
        assertEquals("Pending", service.itemsByType().get("order_status").get(0).value());
        assertEquals(2, service.allItems().size());
    }

    @Test
    void convertsDictionaryFieldsThroughService() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Paid")));
        EnumDictService service = service(registry);

        Order order = new Order();
        service.convert(order);

        assertEquals("Paid", order.status);
    }

    static class Order {
        @DictField(type = "order_status")
        String status = "1";
    }

    private static EnumDictService service(EnumDictRegistry registry) {
        return new DefaultEnumDictService(registry, new EnumDictConverter(registry));
    }
}
