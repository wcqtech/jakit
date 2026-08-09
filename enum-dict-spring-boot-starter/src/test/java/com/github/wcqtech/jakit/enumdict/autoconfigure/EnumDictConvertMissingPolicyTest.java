package com.github.wcqtech.jakit.enumdict.autoconfigure;

import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.autoconfigure.testapp.TestApplication;
import com.github.wcqtech.jakit.enumdict.convert.DictField;
import com.github.wcqtech.jakit.enumdict.convert.EnumDictConverter;
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = TestApplication.class,
        properties = "jakit.enum-dict.convert.missing-policy=FAIL")
class EnumDictConvertMissingPolicyTest {

    @Autowired
    private EnumDictConverter converter;

    @Autowired
    private EnumDictRegistry registry;

    @Test
    void appliesConfiguredMissingPolicyToConverter() {
        assertThrows(IllegalStateException.class, () -> converter.convert(new MissingBean()));
    }

    @Test
    void appliesConfiguredMissingPolicyToRegistryAndUtils() {
        assertThrows(IllegalStateException.class, () -> registry.convert(new MissingBean()));
        assertThrows(IllegalStateException.class, () -> EnumDictUtils.convert(new MissingBean()));
    }

    static class MissingBean {
        @DictField(type = "missing_type")
        String code = "1";
    }
}
