package com.github.wcqtech.enumdict.scanner.fixture.basic;

import com.github.wcqtech.enumdict.EnumDict;
import com.github.wcqtech.enumdict.EnumDictSource;

@EnumDict(type = "annotation_type")
public enum TestWithExtraDict implements EnumDictSource {

    A(1, "A");

    private final int code;
    private final String label;

    TestWithExtraDict(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getDictType() {
        return "InterfaceType";
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
