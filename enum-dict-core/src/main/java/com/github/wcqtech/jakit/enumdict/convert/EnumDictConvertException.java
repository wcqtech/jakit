package com.github.wcqtech.jakit.enumdict.convert;

import com.github.wcqtech.jakit.enumdict.EnumDictException;

/**
 * Thrown when a dictionary key cannot be resolved during conversion and the
 * configured missing policy is {@code FAIL}.
 */
public class EnumDictConvertException extends EnumDictException {

    public EnumDictConvertException(String message) {
        super(message);
    }

    public EnumDictConvertException(String message, Throwable cause) {
        super(message, cause);
    }
}
