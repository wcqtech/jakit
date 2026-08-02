package com.github.wcqtech.enumdict.scanner.fixture.wildcard.beta;

import com.github.wcqtech.enumdict.EnumDictSource;

public enum BetaType implements EnumDictSource {

    B(1, "Beta B");

    private final int code;
    private final String label;

    BetaType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getDictType() {
        return "BetaType";
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
