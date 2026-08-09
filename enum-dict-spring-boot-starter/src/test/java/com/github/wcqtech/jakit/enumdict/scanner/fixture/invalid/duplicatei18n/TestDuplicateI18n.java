package com.github.wcqtech.jakit.enumdict.scanner.fixture.invalid.duplicatei18n;

import com.github.wcqtech.jakit.enumdict.DictI18n;
import com.github.wcqtech.jakit.enumdict.DictKey;
import com.github.wcqtech.jakit.enumdict.DictValue;
import com.github.wcqtech.jakit.enumdict.EnumDict;

@EnumDict
public enum TestDuplicateI18n {

    ONE(1, "One", "one", "one-extra");

    @DictKey
    private final int code;

    @DictValue
    private final String label;

    @DictI18n
    private final String i18nKey;

    @DictI18n
    private final String otherKey;

    TestDuplicateI18n(int code, String label, String i18nKey, String otherKey) {
        this.code = code;
        this.label = label;
        this.i18nKey = i18nKey;
        this.otherKey = otherKey;
    }
}
