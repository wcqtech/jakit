package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.convert.DictField;
import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;
import com.github.wcqtech.jakit.enumdict.i18n.DictValueResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumDictServiceI18nTest {

    private static final DictValueResolver RESOLVER =
            (type, key, i18nKey, fallback, locale) -> "label:" + locale.getLanguage();

    @AfterEach
    void resetLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolvesLabelsForLocale() {
        EnumDictService service = service();

        assertEquals(Optional.of("label:en"), service.valueByKey("order_status", "1", Locale.US));
        assertEquals("order.pending", service.itemByKey("order_status", "1", Locale.US).orElseThrow().i18nKey());
        assertEquals("label:en", service.items("order_status", Locale.US).get(0).value());
    }

    @Test
    void reverseLookupUsesTranslatedLabels() {
        EnumDictService service = service();

        assertEquals(List.of("1", "2"), service.keysByValue("order_status", "label:en", Locale.US));
        assertEquals(List.of(), service.keysByValue("order_status", "Paid", Locale.US));
        assertEquals(2, service.itemsByValue("order_status", "label:en", Locale.US).size());
    }

    @Test
    void usesLocaleContextWhenLocaleIsNull() {
        LocaleContextHolder.setLocale(Locale.US);
        EnumDictService service = service();

        assertEquals(Optional.of("label:en"), service.valueByKey("order_status", "1", null));
    }

    @Test
    void exposesLocalizedSnapshots() {
        EnumDictService service = service();

        assertEquals("label:en", service.itemMap("order_status", Locale.US).get("1").value());
        assertEquals("label:en", service.allItems(Locale.US).get(0).value());
        assertEquals("label:en", service.itemsByType(Locale.US).get("order_status").get(0).value());
    }

    @Test
    void convertsWithLocale() {
        EnumDictService service = service();

        Order order = new Order();
        service.convert(order, Locale.US);

        assertEquals("label:en", order.status);
    }

    @Test
    void nonLocaleMethodsKeepLiteralLabels() {
        EnumDictService service = service();

        assertEquals(Optional.of("Pending"), service.valueByKey("order_status", "1"));
        assertEquals("Pending", service.items("order_status").get(0).value());
    }

    private static EnumDictService service() {
        EnumDictRegistry registry = new EnumDictRegistry();
        registry.register("order_status", List.of(
                new DictItem("order_status", "1", "Pending", "order.pending"),
                new DictItem("order_status", "2", "Paid", "order.paid")));
        EnumDictConverter converter = new EnumDictConverter(registry, RESOLVER);
        return new DefaultEnumDictService(registry, converter, RESOLVER);
    }

    static class Order {
        @DictField(type = "order_status")
        String status = "1";
    }
}
