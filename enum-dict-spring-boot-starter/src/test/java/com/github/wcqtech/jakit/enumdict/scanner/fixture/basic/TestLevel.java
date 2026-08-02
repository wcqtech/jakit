package com.github.wcqtech.jakit.enumdict.scanner.fixture.basic;

import com.github.wcqtech.jakit.enumdict.DictKey;
import com.github.wcqtech.jakit.enumdict.DictValue;
import com.github.wcqtech.jakit.enumdict.EnumDict;

@EnumDict
public enum TestLevel {

    LOW(1, "Low"),
    HIGH(2, "High");

    @DictKey
    private final int code;

    @DictValue
    private final String label;

    TestLevel(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
