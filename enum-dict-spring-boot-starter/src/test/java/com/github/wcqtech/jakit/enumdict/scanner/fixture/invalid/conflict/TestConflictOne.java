package com.github.wcqtech.jakit.enumdict.scanner.fixture.invalid.conflict;

import com.github.wcqtech.jakit.enumdict.EnumDictSource;

public enum TestConflictOne implements EnumDictSource {

    ONE(1, "One");

    private final int code;
    private final String label;

    TestConflictOne(int code, String label) {
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
