package com.github.wcqtech.jakit.enumdict.autoconfigure;

import com.github.wcqtech.jakit.enumdict.autoconfigure.testapp.TestApplication;
import com.github.wcqtech.jakit.enumdict.i18n.DictValueResolver;
import com.github.wcqtech.jakit.enumdict.i18n.EnumDictI18nException;
import com.github.wcqtech.jakit.enumdict.service.EnumDictService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
class EnumDictI18nAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EnumDictService service;

    @Test
    void installsMessageSourceResolverAndTranslatesLabels() {
        assertNotNull(context.getBean(DictValueResolver.class));
        assertEquals("Translated OK", service.valueByKey("AutoConfigTestType", "1", Locale.ENGLISH).orElseThrow());
    }

    @Test
    void fallsBackToLiteralLabelWhenTranslationMissing() {
        assertEquals("Unknown", service.valueByKey("AutoConfigMissingI18nType", "1", Locale.ENGLISH).orElseThrow());
    }
}

@SpringBootTest(classes = TestApplication.class,
        properties = "jakit.enum-dict.i18n.missing-policy=FAIL")
class EnumDictI18nFailPolicyTest {

    @Autowired
    private EnumDictService service;

    @Test
    void failsWhenTranslationMissing() {
        assertThrows(EnumDictI18nException.class,
                () -> service.valueByKey("AutoConfigMissingI18nType", "1", Locale.ENGLISH));
    }
}

@SpringBootTest(classes = TestApplication.class,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration")
class EnumDictI18nWithoutMessageSourceTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EnumDictService service;

    @Test
    void doesNotInstallResolverAndKeepsLiteralLabels() {
        assertTrue(context.getBeansOfType(DictValueResolver.class).isEmpty());
        assertEquals("Unknown", service.valueByKey("AutoConfigMissingI18nType", "1", Locale.ENGLISH).orElseThrow());
    }
}
