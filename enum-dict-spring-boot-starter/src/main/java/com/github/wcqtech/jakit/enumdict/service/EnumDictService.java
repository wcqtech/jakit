package com.github.wcqtech.jakit.enumdict.service;

import com.github.wcqtech.jakit.enumdict.DictItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only query facade over the enum dictionaries registered at startup.
 *
 * Keys are unique within one dictionary type; values (labels) are not
 * guaranteed to be unique. Reverse lookups by value therefore provide both
 * all-matches and first-match methods.
 *
 * All query parameters are required; passing {@code null} throws
 * {@link NullPointerException}.
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
}
