package com.github.wcqtech.enumdict.scanner.fixture.basic;

import com.github.wcqtech.enumdict.EnumDictSource;

public enum TestNumericValue implements EnumDictSource {

    LOW(1, 10),
    HIGH(2, 20);

    private final int code;
    private final int amount;

    TestNumericValue(int code, int amount) {
        this.code = code;
        this.amount = amount;
    }

    @Override
    public String getDictType() {
        return "TestNumericValue";
    }

    @Override
    public Object getDictKey() {
        return code;
    }

    @Override
    public Object getDictValue() {
        return amount;
    }
}
