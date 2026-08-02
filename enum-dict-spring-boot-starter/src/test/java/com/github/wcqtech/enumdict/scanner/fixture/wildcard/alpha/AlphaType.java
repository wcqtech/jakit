package com.github.wcqtech.enumdict.scanner.fixture.wildcard.alpha;

import com.github.wcqtech.enumdict.EnumDictSource;

public enum AlphaType implements EnumDictSource {

    A(1, "Alpha A");

    private final int code;
    private final String label;

    AlphaType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getDictType() {
        return "AlphaType";
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
