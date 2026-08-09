package com.github.wcqtech.jakit.enumdict.scanner.fixture.i18n;

import com.github.wcqtech.jakit.enumdict.DictI18n;
import com.github.wcqtech.jakit.enumdict.DictKey;
import com.github.wcqtech.jakit.enumdict.DictValue;
import com.github.wcqtech.jakit.enumdict.EnumDict;

@EnumDict(type = "i18n_status")
public enum TestI18nStatus {

    PENDING(0, "Pending", "i18n.status.pending"),
    PAID(1, "Paid", "");

    @DictKey
    private final int code;

    @DictValue
    private final String label;

    @DictI18n
    private final String i18nKey;

    TestI18nStatus(int code, String label, String i18nKey) {
        this.code = code;
        this.label = label;
        this.i18nKey = i18nKey;
    }
}
