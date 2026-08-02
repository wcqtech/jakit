package com.github.wcqtech.jakit.enumdict.scanner.fixture.basic;

import com.github.wcqtech.jakit.enumdict.EnumDictSource;

public enum TestDefaultType implements EnumDictSource {

    A(1, "A"),
    B(2, "B");

    private final int code;
    private final String label;

    TestDefaultType(int code, String label) {
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
