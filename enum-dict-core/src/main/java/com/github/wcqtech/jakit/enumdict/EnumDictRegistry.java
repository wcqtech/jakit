package com.github.wcqtech.jakit.enumdict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory dictionary registry with no Spring dependency.
 *
 * Registration is strict: blank types or keys, duplicate keys inside one
 * type, and conflicting re-registration of the same type all fail fast.
 */
public final class EnumDictRegistry {

    private final Map<String, List<DictItem>> itemsByType = new ConcurrentHashMap<>();

    public void register(String type, List<DictItem> items) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(items, "items must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        List<DictItem> snapshot = List.copyOf(items);
        validateItems(type, snapshot);
        List<DictItem> previous = itemsByType.putIfAbsent(type, snapshot);
        if (previous != null && !previous.equals(snapshot)) {
            throw new IllegalStateException("Duplicate dictionary type '" + type
                    + "' registered with different items: " + previous + " vs " + snapshot);
        }
    }

    public List<DictItem> items(String type) {
        return List.copyOf(itemsByType.getOrDefault(type, List.of()));
    }

    public Optional<DictItem> get(String type, String key) {
        Objects.requireNonNull(key, "key must not be null");
        List<DictItem> items = itemsByType.get(type);
        if (items == null) {
            return Optional.empty();
        }
        for (DictItem item : items) {
            if (item.key().equals(key)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public Map<String, DictItem> itemMap(String type) {
        List<DictItem> items = itemsByType.get(type);
        if (items == null) {
            return Map.of();
        }
        Map<String, DictItem> result = new HashMap<>();
        for (DictItem item : items) {
            result.put(item.key(), item);
        }
        return Map.copyOf(result);
    }

    public Set<String> types() {
        return Set.copyOf(itemsByType.keySet());
    }

    public Map<String, List<DictItem>> itemsByType() {
        Map<String, List<DictItem>> result = new HashMap<>();
        for (Map.Entry<String, List<DictItem>> entry : itemsByType.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    public List<DictItem> allItems() {
        List<DictItem> result = new ArrayList<>();
        for (List<DictItem> items : itemsByType.values()) {
            result.addAll(items);
        }
        return List.copyOf(result);
    }

    public boolean contains(String type, String key) {
        return get(type, key).isPresent();
    }

    private static void validateItems(String type, List<DictItem> items) {
        Map<String, String> keys = new HashMap<>();
        for (DictItem item : items) {
            if (item.key().isBlank()) {
                throw new IllegalArgumentException("Dictionary type '" + type + "' contains a blank key");
            }
            String previous = keys.putIfAbsent(item.key(), item.value());
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate key '" + item.key()
                        + "' in dictionary type '" + type + "'");
            }
        }
    }
}
