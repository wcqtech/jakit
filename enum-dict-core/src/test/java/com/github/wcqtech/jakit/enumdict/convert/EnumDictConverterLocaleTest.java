package com.github.wcqtech.jakit.enumdict.convert;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.i18n.DictValueResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumDictConverterLocaleTest {

    private static final DictValueResolver RESOLVER =
            (type, key, i18nKey, fallback, locale) -> "translated:" + locale.getLanguage();

    @Test
    void convertsWithResolvedLabelForLocale() {
        EnumDictRegistry registry = registry();
        EnumDictConverter converter = new EnumDictConverter(registry, RESOLVER);

        Order order = new Order();
        converter.convert(order, Locale.US);

        assertEquals("translated:en", order.status);
    }

    @Test
    void keepsLiteralLabelWithoutLocale() {
        EnumDictRegistry registry = registry();
        EnumDictConverter converter = new EnumDictConverter(registry, RESOLVER);

        Order order = new Order();
        converter.convert(order);

        assertEquals("Pending", order.status);
    }

    @Test
    void keepsLiteralLabelWithoutResolver() {
        EnumDictRegistry registry = registry();
        EnumDictConverter converter = new EnumDictConverter(registry);

        Order order = new Order();
        converter.convert(order, Locale.US);

        assertEquals("Pending", order.status);
    }

    @Test
    void convertsCollectionForLocale() {
        EnumDictRegistry registry = registry();
        EnumDictConverter converter = new EnumDictConverter(registry, RESOLVER);

        Order order = new Order();
        converter.convert(List.of(order), Locale.US);

        assertEquals("translated:en", order.status);
    }

    @Test
    void convertsCollectionWithVisitorForLocale() {
        EnumDictRegistry registry = registry();
        EnumDictConverter converter = new EnumDictConverter(registry, RESOLVER);

        Order order = new Order();
        int[] visits = {0};
        converter.convert(List.of(order), element -> visits[0]++, Locale.US);

        assertEquals("translated:en", order.status);
        assertEquals(1, visits[0]);
    }

    private static EnumDictRegistry registry() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status",
                List.of(new DictItem("order_status", "1", "Pending", "order.pending")));
        return registry;
    }

    static class Order {
        @DictField(type = "order_status")
        String status = "1";
    }
}
