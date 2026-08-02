package com.github.wcqtech.enumdict;

import java.util.Objects;

/**
 * A single dictionary entry exposed to consumers. The key and value are always
 * normalized to {@link String}.
 */
public record DictItem(String type, String key, String value) {

    public DictItem {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
