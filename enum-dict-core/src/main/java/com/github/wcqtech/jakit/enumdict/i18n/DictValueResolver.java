package com.github.wcqtech.jakit.enumdict.i18n;

import java.util.Locale;

/**
 * Resolves the localized display label of a dictionary item. Implementations
 * are free to use Spring's {@code MessageSource}, {@code ResourceBundle}, a
 * database or an external i18n platform.
 */
public interface DictValueResolver {

    /**
     * Resolves the display label for the given dictionary item.
     *
     * @param type the dictionary type
     * @param key the normalized dictionary key
     * @param i18nKey the explicit i18n message key, or {@code null} when the
     *        item declares none
     * @param fallback the literal label used when the translation is missing
     * @param locale the target locale; never {@code null}
     * @return the resolved display label
     * @throws EnumDictI18nException when the missing policy is
     *         {@code FAIL} and the message cannot be resolved
     */
    String resolve(String type, String key, String i18nKey, String fallback, Locale locale);

    /**
     * Returns the message key to look up: the explicit i18n key when present,
     * otherwise the convention {@code {type}.{key}}.
     */
    static String messageKey(String type, String key, String i18nKey) {
        return i18nKey == null || i18nKey.isBlank() ? type + "." + key : i18nKey;
    }
}
