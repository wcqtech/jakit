package com.github.wcqtech.jakit.enumdict.i18n;

import com.github.wcqtech.jakit.enumdict.convert.MissingPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSourceDictValueResolverTest {

    private ResourceBundleMessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n.dict-messages");
    }

    @Test
    void resolvesExplicitI18nKey() {
        DictValueResolver resolver = new MessageSourceDictValueResolver(messageSource);

        assertEquals("Explicit Status",
                resolver.resolve("order_status", "1", "explicit.status", "Pending", Locale.ENGLISH));
    }

    @Test
    void resolvesConventionKeyWhenI18nKeyIsAbsent() {
        DictValueResolver resolver = new MessageSourceDictValueResolver(messageSource);

        assertEquals("Paid", resolver.resolve("order_status", "1", null, "Pending", Locale.ENGLISH));
    }

    @Test
    void ignoresMissingMessageByDefault() {
        DictValueResolver resolver = new MessageSourceDictValueResolver(messageSource);

        assertEquals("Unknown", resolver.resolve("pay_channel", "9", null, "Unknown", Locale.ENGLISH));
    }

    @Test
    void failsWhenMissingMessageAndPolicyIsFail() {
        DictValueResolver resolver = new MessageSourceDictValueResolver(messageSource, MissingPolicy.FAIL);

        EnumDictI18nException ex = assertThrows(EnumDictI18nException.class,
                () -> resolver.resolve("pay_channel", "9", null, "Unknown", Locale.ENGLISH));

        assertTrue(ex.getMessage().contains("pay_channel"));
    }
}
