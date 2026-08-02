package com.github.wcqtech.enumdict.service;

import com.github.wcqtech.enumdict.DictItem;
import com.github.wcqtech.enumdict.EnumDictRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Default {@link EnumDictService} delegating to {@link EnumDictRegistry}.
 */
public class DefaultEnumDictService implements EnumDictService {

    private final EnumDictRegistry registry;

    public DefaultEnumDictService(EnumDictRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
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
}
