package com.github.wcqtech.jakit.enumdict.i18n;

import com.github.wcqtech.jakit.enumdict.EnumDictException;

/**
 * Thrown when a dictionary label cannot be resolved and the configured
 * missing policy is {@code FAIL}.
 */
public class EnumDictI18nException extends EnumDictException {

    public EnumDictI18nException(String message) {
        super(message);
    }

    public EnumDictI18nException(String message, Throwable cause) {
        super(message, cause);
    }
}
