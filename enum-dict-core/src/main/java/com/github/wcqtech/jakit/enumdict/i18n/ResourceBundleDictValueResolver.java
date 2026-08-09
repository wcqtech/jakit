package com.github.wcqtech.jakit.enumdict.i18n;

import com.github.wcqtech.jakit.enumdict.convert.MissingPolicy;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * {@link DictValueResolver} backed by {@link ResourceBundle}.
 */
public final class ResourceBundleDictValueResolver implements DictValueResolver {

    private final String baseName;
    private final MissingPolicy missingPolicy;

    public ResourceBundleDictValueResolver(String baseName) {
        this(baseName, MissingPolicy.IGNORE);
    }

    public ResourceBundleDictValueResolver(String baseName, MissingPolicy missingPolicy) {
        this.baseName = Objects.requireNonNull(baseName, "baseName must not be null");
        this.missingPolicy = Objects.requireNonNull(missingPolicy, "missingPolicy must not be null");
    }

    @Override
    public String resolve(String type, String key, String i18nKey, String fallback, Locale locale) {
        Locale target = locale != null ? locale : Locale.getDefault();
        String code = DictValueResolver.messageKey(type, key, i18nKey);
        try {
            return ResourceBundle.getBundle(baseName, target).getString(code);
        } catch (MissingResourceException e) {
            if (missingPolicy == MissingPolicy.FAIL) {
                throw new EnumDictI18nException("Missing i18n message for dictionary type '" + type
                        + "', key '" + key + "', message key '" + code + "', locale " + target, e);
            }
            return fallback;
        }
    }
}
