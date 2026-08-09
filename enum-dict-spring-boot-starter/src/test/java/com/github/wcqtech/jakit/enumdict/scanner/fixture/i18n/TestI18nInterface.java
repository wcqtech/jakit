package com.github.wcqtech.jakit.enumdict.scanner.fixture.i18n;

import com.github.wcqtech.jakit.enumdict.EnumDictSource;

public enum TestI18nInterface implements EnumDictSource {

    ALPHA(1, "Alpha");

    private final int code;
    private final String label;

    TestI18nInterface(int code, String label) {
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

    @Override
    public String getDictI18nKey() {
        return "i18n.interface.alpha";
    }
}
