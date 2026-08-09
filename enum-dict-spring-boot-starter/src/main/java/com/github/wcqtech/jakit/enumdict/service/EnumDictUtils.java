package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

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
 * passing {@code null} for type, key or value throws
 * {@link NullPointerException}; a {@code null} locale resolves the current
 * locale.
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

    /**
     * Converts dictionary keys on the fields of the given object into
     * dictionary values, including nested beans, collections and maps.
     *
     * @param target the object to convert
     * @param <T> the object type
     * @return the same object instance
     */
    public static <T> T convert(T target) {
        return service().convert(target);
    }

    /**
     * Converts every element of the given collection. Null elements are
     * skipped.
     *
     * @param targets the elements to convert
     * @param <T> the element type
     */
    public static <T> void convert(Collection<T> targets) {
        service().convert(targets);
    }

    /**
     * Converts every element of the given collection, then invokes the visitor
     * on each converted element.
     *
     * @param targets the elements to convert
     * @param visitor invoked after each element is converted
     * @param <T> the element type
     */
    public static <T> void convert(Collection<T> targets, Consumer<? super T> visitor) {
        service().convert(targets, visitor);
    }

    /**
     * Returns all dictionary items of the given type in declaration order,
     * with display labels resolved for the given locale.
     */
    public static List<DictItem> items(String type, Locale locale) {
        return service().items(type, locale);
    }

    /**
     * Returns the localized dictionary item identified by the given key.
     */
    public static Optional<DictItem> getItemByKey(String type, String key, Locale locale) {
        return service().itemByKey(type, key, locale);
    }

    /**
     * Returns all localized dictionary items whose display label matches.
     */
    public static List<DictItem> getItemsByValue(String type, String value, Locale locale) {
        return service().itemsByValue(type, value, locale);
    }

    /**
     * Returns the first localized dictionary item whose display label matches.
     */
    public static Optional<DictItem> getItemByValue(String type, String value, Locale locale) {
        return service().itemByValue(type, value, locale);
    }

    /**
     * Returns the localized display label for the given key.
     */
    public static Optional<String> getValueByKey(String type, String key, Locale locale) {
        return service().valueByKey(type, key, locale);
    }

    /**
     * Returns all dictionary keys whose localized display label matches.
     */
    public static List<String> getKeysByValue(String type, String value, Locale locale) {
        return service().keysByValue(type, value, locale);
    }

    /**
     * Returns the first dictionary key whose localized display label matches.
     */
    public static Optional<String> getKeyByValue(String type, String value, Locale locale) {
        return service().keyByValue(type, value, locale);
    }

    /**
     * Returns all dictionary items of the given type keyed by their key, with
     * display labels resolved for the given locale.
     */
    public static Map<String, DictItem> itemMap(String type, Locale locale) {
        return service().itemMap(type, locale);
    }

    /**
     * Returns all dictionary items grouped by type, with display labels
     * resolved for the given locale.
     */
    public static Map<String, List<DictItem>> itemsByType(Locale locale) {
        return service().itemsByType(locale);
    }

    /**
     * Returns all dictionary items as a flat list, with display labels
     * resolved for the given locale.
     */
    public static List<DictItem> allItems(Locale locale) {
        return service().allItems(locale);
    }

    /**
     * Converts dictionary keys on the fields of the given object into
     * localized display labels.
     */
    public static <T> T convert(T target, Locale locale) {
        return service().convert(target, locale);
    }

    /**
     * Converts every element of the given collection into localized display
     * labels.
     *
     * Note: this overload and {@code convert(Collection, Consumer)} share the
     * same first parameter type, so passing a bare {@code null} as the second
     * argument is ambiguous and will not compile. Pass an explicitly typed
     * {@link Locale} or {@link Consumer}, or use the single-argument
     * {@code convert(Collection)} when neither is needed.
     */
    public static <T> void convert(Collection<T> targets, Locale locale) {
        service().convert(targets, locale);
    }

    /**
     * Converts every element of the given collection into localized display
     * labels, then invokes the visitor on each converted element.
     */
    public static <T> void convert(Collection<T> targets, Consumer<? super T> visitor, Locale locale) {
        service().convert(targets, visitor, locale);
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
