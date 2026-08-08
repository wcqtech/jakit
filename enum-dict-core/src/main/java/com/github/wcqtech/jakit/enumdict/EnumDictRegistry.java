package com.github.wcqtech.jakit.enumdict;

import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Thread-safe, in-memory dictionary registry with no Spring dependency.
 *
 * Registration is strict: blank types or keys, duplicate keys inside one
 * type, and conflicting re-registration of the same type all fail fast.
 */
public final class EnumDictRegistry {

    private final Map<String, List<DictItem>> itemsByType = new ConcurrentHashMap<>();
    private final EnumDictConverter converter = new EnumDictConverter(this);

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

    /**
     * Converts dictionary keys on the fields of the given object into
     * dictionary values, including nested beans, collections and maps.
     *
     * @param target the object to convert
     * @param <T> the object type
     * @return the same object instance
     */
    public <T> T convert(T target) {
        return converter.convert(target);
    }

    /**
     * Converts every element of the given collection.
     *
     * @param targets the elements to convert
     * @param <T> the element type
     */
    public <T> void convert(Collection<T> targets) {
        converter.convert(targets);
    }

    /**
     * Converts every element of the given collection, then invokes the visitor
     * on each converted element.
     *
     * @param targets the elements to convert
     * @param visitor invoked after each element is converted
     * @param <T> the element type
     */
    public <T> void convert(Collection<T> targets, Consumer<? super T> visitor) {
        converter.convert(targets, visitor);
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
