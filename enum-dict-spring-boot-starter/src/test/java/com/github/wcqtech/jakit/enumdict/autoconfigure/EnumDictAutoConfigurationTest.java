package com.github.wcqtech.jakit.enumdict.autoconfigure;

import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import com.github.wcqtech.jakit.enumdict.autoconfigure.testapp.TestApplication;
import com.github.wcqtech.jakit.enumdict.service.EnumDictService;
import com.github.wcqtech.jakit.enumdict.service.EnumDictUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
class EnumDictAutoConfigurationTest {

    @Autowired
    private EnumDictRegistry registry;

    @Autowired
    private EnumDictService service;

    @Test
    void registersEnumsFromDefaultBasePackage() {
        assertTrue(registry.contains("AutoConfigTestType", "1"));
        assertTrue(service.contains("AutoConfigTestType", "1"));
        assertEquals(Optional.of("OK"), EnumDictUtils.getValueByKey("AutoConfigTestType", "1"));
    }
}
