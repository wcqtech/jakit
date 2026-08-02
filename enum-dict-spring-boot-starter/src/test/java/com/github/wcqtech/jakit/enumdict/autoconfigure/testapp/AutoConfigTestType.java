package com.github.wcqtech.jakit.enumdict.autoconfigure.testapp;

import com.github.wcqtech.jakit.enumdict.EnumDictSource;

public enum AutoConfigTestType implements EnumDictSource {

    OK(1, "OK");

    private final int code;
    private final String label;

    AutoConfigTestType(int code, String label) {
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
