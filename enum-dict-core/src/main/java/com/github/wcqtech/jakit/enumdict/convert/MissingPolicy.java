package com.github.wcqtech.jakit.enumdict.convert;

/**
 * Behavior when a dictionary key is not found during conversion.
 */
public enum MissingPolicy {

    /**
     * Leave the target field unchanged.
     */
    IGNORE,

    /**
     * Throw an exception with the dictionary type, key, field and object type.
     */
    FAIL
}
