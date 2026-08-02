package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Static convenience facade over {@link EnumDictService}.
 *
 * The backing service is installed by the enum-dict auto-configuration after
 * scanning, so these methods are usable once the Spring context has finished
 * initialization. Calling any method before the backing service is installed
 * throws {@link IllegalStateException}.
 *
 * Prefer injecting {@link EnumDictService} in tests or when mocking is
 * required.
 *
 * Query parameters follow the same contract as {@link EnumDictService}:
 * passing {@code null} throws {@link NullPointerException}.
 */
public final class EnumDictUtils {

    private static volatile EnumDictService service;

    private EnumDictUtils() {
    }

    /**
     * Installs the backing service used by all static query methods.
     *
     * Normally called by the enum-dict auto-configuration. Exposed for custom
     * wiring and tests.
     *
     * @param service the service to use; must not be null
     */
    public static void setService(EnumDictService service) {
        EnumDictUtils.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * Returns all dictionary items of the given type in declaration order.
     *
     * @param type the dictionary type
     * @return an unmodifiable list of items; empty when the type is not registered
     */
    public static List<DictItem> items(String type) {
        return service().items(type);
    }

    /**
     * Returns the dictionary item identified by the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @return the matching item, or empty when the type or key is not registered
     */
    public static Optional<DictItem> getItemByKey(String type, String key) {
        return service().itemByKey(type, key);
    }

    /**
     * Returns all dictionary items whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return an unmodifiable list of matching items; empty when there is no match
     */
    public static List<DictItem> getItemsByValue(String type, String value) {
        return service().itemsByValue(type, value);
    }

    /**
     * Returns the first dictionary item whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return the first matching item, or empty when there is no match
     */
    public static Optional<DictItem> getItemByValue(String type, String value) {
        return service().itemByValue(type, value);
    }

    /**
     * Returns all dictionary keys whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return an unmodifiable list of matching keys; empty when there is no match
     */
    public static List<String> getKeysByValue(String type, String value) {
        return service().keysByValue(type, value);
    }

    /**
     * Returns the first dictionary key whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return the first matching key, or empty when there is no match
     */
    public static Optional<String> getKeyByValue(String type, String value) {
        return service().keyByValue(type, value);
    }

    /**
     * Returns the value (label) for the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @return the value, or empty when the type or key is not registered
     */
    public static Optional<String> getValueByKey(String type, String key) {
        return service().valueByKey(type, key);
    }

    /**
     * Returns all dictionary items of the given type keyed by their key.
     *
     * @param type the dictionary type
     * @return an unmodifiable map; empty when the type is not registered
     */
    public static Map<String, DictItem> itemMap(String type) {
        return service().itemMap(type);
    }

    /**
     * Returns all registered dictionary types.
     *
     * @return an unmodifiable set of registered types
     */
    public static Set<String> types() {
        return service().types();
    }

    /**
     * Returns all dictionary items grouped by type.
     *
     * @return an unmodifiable map of type to items; empty when nothing is
     * registered. Values preserve declaration order; map order is unspecified.
     */
    public static Map<String, List<DictItem>> itemsByType() {
        return service().itemsByType();
    }

    /**
     * Returns all dictionary items across every registered type as a flat list.
     *
     * @return an unmodifiable list; empty when nothing is registered. Declaration
     * order is preserved within each type; cross-type order is unspecified.
     */
    public static List<DictItem> allItems() {
        return service().allItems();
    }

    /**
     * Returns whether the given dictionary type contains the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @return true when the item exists
     */
    public static boolean contains(String type, String key) {
        return service().contains(type, key);
    }

    private static EnumDictService service() {
        EnumDictService current = service;
        if (current == null) {
            throw new IllegalStateException(
                    "EnumDictService is not initialized; make sure the enum-dict starter auto-configuration has run");
        }
        return current;
    }
}
