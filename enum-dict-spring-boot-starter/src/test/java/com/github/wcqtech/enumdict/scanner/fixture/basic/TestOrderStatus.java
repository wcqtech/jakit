package com.github.wcqtech.enumdict.scanner.fixture.basic;

import com.github.wcqtech.enumdict.EnumDictSource;

public enum TestOrderStatus implements EnumDictSource {

    PENDING(0, "Pending"),
    PAID(1, "Paid");

    private final int code;
    private final String label;

    TestOrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getDictType() {
        return "TestOrderStatus";
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
