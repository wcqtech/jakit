package com.github.wcqtech.jakit.enumdict.scanner.fixture.basic;

import com.github.wcqtech.jakit.enumdict.DictKey;
import com.github.wcqtech.jakit.enumdict.DictValue;
import com.github.wcqtech.jakit.enumdict.EnumDict;

@EnumDict(type = "pay_channel")
public enum TestPayChannel {

    WECHAT(1, "WeChat"),
    ALIPAY(2, "Alipay");

    @DictKey
    private final int code;

    @DictValue
    private final String label;

    TestPayChannel(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
