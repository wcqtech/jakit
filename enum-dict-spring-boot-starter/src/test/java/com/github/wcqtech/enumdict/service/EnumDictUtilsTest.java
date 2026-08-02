package com.github.wcqtech.enumdict.service;

import com.github.wcqtech.enumdict.DictItem;
import com.github.wcqtech.enumdict.EnumDictRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumDictUtilsTest {

    @Test
    void delegatesAfterServiceIsInstalled() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Paid")));
        EnumDictUtils.setService(new DefaultEnumDictService(registry));

        assertEquals(Optional.of("Paid"), EnumDictUtils.getValueByKey("order_status", "1"));
        assertEquals("Paid", EnumDictUtils.getItemByKey("order_status", "1").orElseThrow().value());
    }

    @Test
    void supportsReverseLookupByValue() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(
                new DictItem("order_status", "1", "Pending"),
                new DictItem("order_status", "2", "Paid"),
                new DictItem("order_status", "3", "Paid")));
        EnumDictUtils.setService(new DefaultEnumDictService(registry));

        assertEquals(List.of("2", "3"), EnumDictUtils.getKeysByValue("order_status", "Paid"));
        assertEquals(Optional.of("2"), EnumDictUtils.getKeyByValue("order_status", "Paid"));
        assertEquals(2, EnumDictUtils.getItemsByValue("order_status", "Paid").size());
    }

    @Test
    void exposesFullDictionarySnapshots() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(new DictItem("order_status", "1", "Pending")));
        registry.register("pay_channel", List.of(new DictItem("pay_channel", "1", "WeChat")));
        EnumDictUtils.setService(new DefaultEnumDictService(registry));

        assertEquals(2, EnumDictUtils.itemsByType().size());
        assertEquals(2, EnumDictUtils.allItems().size());
    }
}
