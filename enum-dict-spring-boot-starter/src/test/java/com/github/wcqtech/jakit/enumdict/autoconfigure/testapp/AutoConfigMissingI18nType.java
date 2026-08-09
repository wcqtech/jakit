package com.github.wcqtech.jakit.enumdict.autoconfigure.testapp;

import com.github.wcqtech.jakit.enumdict.EnumDictSource;

public enum AutoConfigMissingI18nType implements EnumDictSource {

    UNKNOWN(1, "Unknown");

    private final int code;
    private final String label;

    AutoConfigMissingI18nType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public Object getDictKey() {
        return code;
    }

    @Override
    public String getDictValue() {
        return label;
    }
}
