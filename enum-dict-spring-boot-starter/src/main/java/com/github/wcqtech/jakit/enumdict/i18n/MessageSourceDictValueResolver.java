package com.github.wcqtech.jakit.enumdict.i18n;

import com.github.wcqtech.jakit.enumdict.convert.MissingPolicy;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import java.util.Locale;
import java.util.Objects;

/**
 * {@link DictValueResolver} backed by Spring's {@link MessageSource}.
 */
public final class MessageSourceDictValueResolver implements DictValueResolver {

    private final MessageSource messageSource;
    private final MissingPolicy missingPolicy;

    public MessageSourceDictValueResolver(MessageSource messageSource) {
        this(messageSource, MissingPolicy.IGNORE);
    }

    public MessageSourceDictValueResolver(MessageSource messageSource, MissingPolicy missingPolicy) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
        this.missingPolicy = Objects.requireNonNull(missingPolicy, "missingPolicy must not be null");
    }

    @Override
    public String resolve(String type, String key, String i18nKey, String fallback, Locale locale) {
        Locale target = locale != null ? locale : Locale.getDefault();
        String code = DictValueResolver.messageKey(type, key, i18nKey);
        try {
            return messageSource.getMessage(code, null, target);
        } catch (NoSuchMessageException e) {
            if (missingPolicy == MissingPolicy.FAIL) {
                throw new EnumDictI18nException("Missing i18n message for dictionary type '" + type
                        + "', key '" + key + "', message key '" + code + "', locale " + target, e);
            }
            return fallback;
        }
    }
}
