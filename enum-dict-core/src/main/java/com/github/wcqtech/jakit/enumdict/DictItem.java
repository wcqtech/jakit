package com.github.wcqtech.jakit.enumdict;

import java.util.Objects;

/**
 * A single dictionary entry exposed to consumers. The key and value are always
 * normalized to {@link String}. The optional {@code i18nKey} is the message
 * key used to resolve localized labels; it stays {@code null} for enums that
 * do not participate in i18n.
 */
public record DictItem(String type, String key, String value, String i18nKey) {

    public DictItem(String type, String key, String value) {
        this(type, key, value, null);
    }

    public DictItem {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        i18nKey = normalizeI18nKey(i18nKey);
    }

    private static String normalizeI18nKey(String i18nKey) {
        return i18nKey == null || i18nKey.isBlank() ? null : i18nKey;
    }
}
