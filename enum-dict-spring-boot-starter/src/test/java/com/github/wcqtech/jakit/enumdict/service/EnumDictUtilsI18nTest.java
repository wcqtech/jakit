package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.convert.DictField;
import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;
import com.github.wcqtech.jakit.enumdict.i18n.DictValueResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumDictUtilsI18nTest {

    @Test
    void resolvesLabelsForLocale() {
        EnumDictUtils.setService(service());

        assertEquals(Optional.of("label:en"), EnumDictUtils.getValueByKey("order_status", "1", Locale.US));
        assertEquals("order.pending", EnumDictUtils.getItemByKey("order_status", "1", Locale.US).orElseThrow().i18nKey());
        assertEquals(List.of("1"), EnumDictUtils.getKeysByValue("order_status", "label:en", Locale.US));
        assertEquals(1, EnumDictUtils.itemsByType(Locale.US).size());
        assertEquals("label:en", EnumDictUtils.allItems(Locale.US).get(0).value());
    }

    @Test
    void convertsWithLocale() {
        EnumDictUtils.setService(service());

        Order order = new Order();
        EnumDictUtils.convert(order, Locale.US);

        assertEquals("label:en", order.status);
    }

    private static EnumDictService service() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status",
                List.of(new DictItem("order_status", "1", "Pending", "order.pending")));
        DictValueResolver resolver =
                (type, key, i18nKey, fallback, locale) -> "label:" + locale.getLanguage();
        EnumDictConverter converter = new EnumDictConverter(registry, resolver);
        return new DefaultEnumDictService(registry, converter, resolver);
    }

    static class Order {
        @DictField(type = "order_status")
        String status = "1";
    }
}
