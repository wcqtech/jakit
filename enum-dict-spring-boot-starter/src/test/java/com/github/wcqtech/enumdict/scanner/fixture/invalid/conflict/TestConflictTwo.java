package com.github.wcqtech.enumdict.scanner.fixture.invalid.conflict;

import com.github.wcqtech.enumdict.EnumDictSource;

public enum TestConflictTwo implements EnumDictSource {

    TWO(2, "Two");

    private final int code;
    private final String label;

    TestConflictTwo(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getDictType() {
        return "shared_type";
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
