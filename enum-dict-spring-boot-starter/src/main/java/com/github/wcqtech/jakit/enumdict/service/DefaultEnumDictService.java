package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;
import com.github.wcqtech.jakit.enumdict.i18n.DictValueResolver;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default {@link EnumDictService} delegating queries to
 * {@link EnumDictRegistry} and conversion to {@link EnumDictConverter}.
 */
public class DefaultEnumDictService implements EnumDictService {

    private final EnumDictRegistry registry;
    private final EnumDictConverter converter;
    private final DictValueResolver valueResolver;

    public DefaultEnumDictService(EnumDictRegistry registry, EnumDictConverter converter) {
        this(registry, converter, null);
    }

    public DefaultEnumDictService(EnumDictRegistry registry, EnumDictConverter converter,
                                  DictValueResolver valueResolver) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.converter = Objects.requireNonNull(converter, "converter must not be null");
        this.valueResolver = valueResolver;
    }

    @Override
    public List<DictItem> items(String type) {
        Objects.requireNonNull(type, "type must not be null");
        return registry.items(type);
    }

    @Override
    public Optional<DictItem> itemByKey(String type, String key) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return registry.get(type, key);
    }

    @Override
    public List<DictItem> itemsByValue(String type, String value) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return items(type).stream()
                .filter(item -> Objects.equals(item.value(), value))
                .toList();
    }

    @Override
    public Optional<DictItem> itemByValue(String type, String value) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return itemsByValue(type, value).stream().findFirst();
    }

    @Override
    public Optional<String> valueByKey(String type, String key) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return itemByKey(type, key).map(DictItem::value);
    }

    @Override
    public List<String> keysByValue(String type, String value) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return itemsByValue(type, value).stream()
                .map(DictItem::key)
                .toList();
    }

    @Override
    public Optional<String> keyByValue(String type, String value) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return itemByValue(type, value).map(DictItem::key);
    }

    @Override
    public Map<String, DictItem> itemMap(String type) {
        Objects.requireNonNull(type, "type must not be null");
        return registry.itemMap(type);
    }

    @Override
    public Set<String> types() {
        return registry.types();
    }

    @Override
    public Map<String, List<DictItem>> itemsByType() {
        return registry.itemsByType();
    }

    @Override
    public List<DictItem> allItems() {
        return registry.allItems();
    }

    @Override
    public boolean contains(String type, String key) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return registry.contains(type, key);
    }

    @Override
    public <T> T convert(T target) {
        return converter.convert(target);
    }

    @Override
    public <T> void convert(Collection<T> targets) {
        converter.convert(targets);
    }

    @Override
    public <T> void convert(Collection<T> targets, Consumer<? super T> visitor) {
        converter.convert(targets, visitor);
    }

    @Override
    public List<DictItem> items(String type, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Locale target = resolveLocale(locale);
        return registry.items(type).stream()
                .map(item -> localize(item, target))
                .toList();
    }

    @Override
    public Optional<DictItem> itemByKey(String type, String key, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return registry.get(type, key).map(item -> localize(item, resolveLocale(locale)));
    }

    @Override
    public List<DictItem> itemsByValue(String type, String value, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Locale target = resolveLocale(locale);
        return registry.items(type).stream()
                .map(item -> localize(item, target))
                .filter(item -> Objects.equals(item.value(), value))
                .toList();
    }

    @Override
    public Optional<DictItem> itemByValue(String type, String value, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return itemsByValue(type, value, locale).stream().findFirst();
    }

    @Override
    public Optional<String> valueByKey(String type, String key, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return itemByKey(type, key, locale).map(DictItem::value);
    }

    @Override
    public List<String> keysByValue(String type, String value, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return itemsByValue(type, value, locale).stream()
                .map(DictItem::key)
                .toList();
    }

    @Override
    public Optional<String> keyByValue(String type, String value, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return itemByValue(type, value, locale).map(DictItem::key);
    }

    @Override
    public Map<String, DictItem> itemMap(String type, Locale locale) {
        Objects.requireNonNull(type, "type must not be null");
        Locale target = resolveLocale(locale);
        Map<String, DictItem> result = new HashMap<>();
        for (DictItem item : registry.items(type)) {
            result.put(item.key(), localize(item, target));
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<String, List<DictItem>> itemsByType(Locale locale) {
        Locale target = resolveLocale(locale);
        Map<String, List<DictItem>> result = new HashMap<>();
        for (String type : registry.types()) {
            result.put(type, registry.items(type).stream()
                    .map(item -> localize(item, target))
                    .toList());
        }
        return Map.copyOf(result);
    }

    @Override
    public List<DictItem> allItems(Locale locale) {
        Locale target = resolveLocale(locale);
        return registry.allItems().stream()
                .map(item -> localize(item, target))
                .toList();
    }

    @Override
    public <T> T convert(T target, Locale locale) {
        return converter.convert(target, resolveLocale(locale));
    }

    @Override
    public <T> void convert(Collection<T> targets, Locale locale) {
        converter.convert(targets, resolveLocale(locale));
    }

    @Override
    public <T> void convert(Collection<T> targets, Consumer<? super T> visitor, Locale locale) {
        converter.convert(targets, visitor, resolveLocale(locale));
    }

    private DictItem localize(DictItem item, Locale locale) {
        if (valueResolver == null) {
            return item;
        }
        String label = valueResolver.resolve(item.type(), item.key(), item.i18nKey(), item.value(), locale);
        return new DictItem(item.type(), item.key(), label, item.i18nKey());
    }

    private static Locale resolveLocale(Locale locale) {
        return locale != null ? locale : LocaleContextHolder.getLocale();
    }
}
