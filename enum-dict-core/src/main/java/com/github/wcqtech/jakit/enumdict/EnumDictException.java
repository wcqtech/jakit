package com.github.wcqtech.jakit.enumdict;

/**
 * Base exception for enum-dict feature-level failures.
 */
public class EnumDictException extends RuntimeException {

    public EnumDictException(String message) {
        super(message);
    }

    public EnumDictException(String message, Throwable cause) {
        super(message, cause);
    }
}
