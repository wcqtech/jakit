package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Read-only query facade over the enum dictionaries registered at startup.
 *
 * Keys are unique within one dictionary type; values (labels) are not
 * guaranteed to be unique. Reverse lookups by value therefore provide both
 * all-matches and first-match methods.
 *
 * Query parameters except locale are required; passing {@code null} for type,
 * key or value throws {@link NullPointerException}. A {@code null} locale
 * resolves to the current {@code LocaleContextHolder} locale, or to
 * {@link Locale#getDefault()} when no context is available.
 */
public interface EnumDictService {

    /**
     * Returns all dictionary items of the given type in declaration order.
     *
     * @param type the dictionary type
     * @return an unmodifiable list of items; empty when the type is not registered
     * @throws NullPointerException if {@code type} is null
     */
    List<DictItem> items(String type);

    /**
     * Returns the dictionary item identified by the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @return the matching item, or empty when the type or key is not registered
     * @throws NullPointerException if {@code type} or {@code key} is null
     */
    Optional<DictItem> itemByKey(String type, String key);

    /**
     * Returns all dictionary items whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return an unmodifiable list of matching items; empty when there is no match
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    List<DictItem> itemsByValue(String type, String value);

    /**
     * Returns the first dictionary item whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return the first matching item, or empty when there is no match
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    Optional<DictItem> itemByValue(String type, String value);

    /**
     * Returns the value (label) for the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @return the value, or empty when the type or key is not registered
     * @throws NullPointerException if {@code type} or {@code key} is null
     */
    Optional<String> valueByKey(String type, String key);

    /**
     * Returns all dictionary keys whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return an unmodifiable list of matching keys; empty when there is no match
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    List<String> keysByValue(String type, String value);

    /**
     * Returns the first dictionary key whose value matches, in declaration order.
     *
     * @param type the dictionary type
     * @param value the dictionary value (label)
     * @return the first matching key, or empty when there is no match
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    Optional<String> keyByValue(String type, String value);

    /**
     * Returns all dictionary items of the given type keyed by their key.
     *
     * @param type the dictionary type
     * @return an unmodifiable map; empty when the type is not registered
     * @throws NullPointerException if {@code type} is null
     */
    Map<String, DictItem> itemMap(String type);

    /**
     * Returns all registered dictionary types.
     *
     * @return an unmodifiable set of registered types
     */
    Set<String> types();

    /**
     * Returns all dictionary items grouped by type.
     *
     * @return an unmodifiable map of type to items; empty when nothing is
     * registered. Values preserve declaration order; map order is unspecified.
     */
    Map<String, List<DictItem>> itemsByType();

    /**
     * Returns all dictionary items across every registered type as a flat list.
     *
     * @return an unmodifiable list; empty when nothing is registered. Declaration
     * order is preserved within each type; cross-type order is unspecified.
     */
    List<DictItem> allItems();

    /**
     * Returns whether the given dictionary type contains the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @return true when the item exists
     * @throws NullPointerException if {@code type} or {@code key} is null
     */
    boolean contains(String type, String key);

    /**
     * Converts dictionary keys on the fields of the given object into
     * dictionary values, including nested beans, collections and maps.
     *
     * @param target the object to convert
     * @param <T> the object type
     * @return the same object instance
     * @throws NullPointerException if {@code target} is null
     */
    <T> T convert(T target);

    /**
     * Converts every element of the given collection. Null elements are
     * skipped.
     *
     * @param targets the elements to convert
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} is null
     */
    <T> void convert(Collection<T> targets);

    /**
     * Converts every element of the given collection, then invokes the visitor
     * on each converted element.
     *
     * @param targets the elements to convert
     * @param visitor invoked after each element is converted
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} or {@code visitor} is null
     */
    <T> void convert(Collection<T> targets, Consumer<? super T> visitor);

    /**
     * Returns all dictionary items of the given type in declaration order,
     * with display labels resolved for the given locale.
     *
     * @param type the dictionary type
     * @param locale the target locale
     * @return an unmodifiable list of localized items; empty when the type is not registered
     * @throws NullPointerException if {@code type} is null
     */
    default List<DictItem> items(String type, Locale locale) {
        return items(type);
    }

    /**
     * Returns the localized dictionary item identified by the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @param locale the target locale
     * @return the matching localized item, or empty when the type or key is not registered
     * @throws NullPointerException if {@code type} or {@code key} is null
     */
    default Optional<DictItem> itemByKey(String type, String key, Locale locale) {
        return itemByKey(type, key);
    }

    /**
     * Returns all localized dictionary items whose display label matches.
     *
     * @param type the dictionary type
     * @param value the localized display label
     * @param locale the target locale
     * @return an unmodifiable list of matching items
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    default List<DictItem> itemsByValue(String type, String value, Locale locale) {
        return itemsByValue(type, value);
    }

    /**
     * Returns the first localized dictionary item whose display label matches.
     *
     * @param type the dictionary type
     * @param value the localized display label
     * @param locale the target locale
     * @return the first matching item, or empty when there is no match
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    default Optional<DictItem> itemByValue(String type, String value, Locale locale) {
        return itemByValue(type, value);
    }

    /**
     * Returns the localized display label for the given key.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @param locale the target locale
     * @return the localized value, or empty when the type or key is not registered
     * @throws NullPointerException if {@code type} or {@code key} is null
     */
    default Optional<String> valueByKey(String type, String key, Locale locale) {
        return valueByKey(type, key);
    }

    /**
     * Returns all dictionary keys whose localized display label matches.
     *
     * @param type the dictionary type
     * @param value the localized display label
     * @param locale the target locale
     * @return an unmodifiable list of matching keys
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    default List<String> keysByValue(String type, String value, Locale locale) {
        return keysByValue(type, value);
    }

    /**
     * Returns the first dictionary key whose localized display label matches.
     *
     * @param type the dictionary type
     * @param value the localized display label
     * @param locale the target locale
     * @return the first matching key, or empty when there is no match
     * @throws NullPointerException if {@code type} or {@code value} is null
     */
    default Optional<String> keyByValue(String type, String value, Locale locale) {
        return keyByValue(type, value);
    }

    /**
     * Returns all dictionary items of the given type keyed by their key, with
     * display labels resolved for the given locale.
     *
     * @param type the dictionary type
     * @param locale the target locale
     * @return an unmodifiable map; empty when the type is not registered
     * @throws NullPointerException if {@code type} is null
     */
    default Map<String, DictItem> itemMap(String type, Locale locale) {
        return itemMap(type);
    }

    /**
     * Returns all dictionary items grouped by type, with display labels
     * resolved for the given locale.
     *
     * @param locale the target locale
     * @return an unmodifiable map of type to localized items
     */
    default Map<String, List<DictItem>> itemsByType(Locale locale) {
        return itemsByType();
    }

    /**
     * Returns all dictionary items as a flat list, with display labels
     * resolved for the given locale.
     *
     * @param locale the target locale
     * @return an unmodifiable list of localized items
     */
    default List<DictItem> allItems(Locale locale) {
        return allItems();
    }

    /**
     * Converts dictionary keys on the fields of the given object into
     * localized display labels.
     *
     * @param target the object to convert
     * @param locale the target locale
     * @param <T> the object type
     * @return the same object instance
     * @throws NullPointerException if {@code target} is null
     */
    default <T> T convert(T target, Locale locale) {
        return convert(target);
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
     *
     * @param targets the elements to convert
     * @param locale the target locale
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} is null
     */
    default <T> void convert(Collection<T> targets, Locale locale) {
        convert(targets);
    }

    /**
     * Converts every element of the given collection into localized display
     * labels, then invokes the visitor on each converted element.
     *
     * @param targets the elements to convert
     * @param visitor invoked after each element is converted
     * @param locale the target locale
     * @param <T> the element type
     * @throws NullPointerException if {@code targets} or {@code visitor} is null
     */
    default <T> void convert(Collection<T> targets, Consumer<? super T> visitor, Locale locale) {
        convert(targets, visitor);
    }
}
