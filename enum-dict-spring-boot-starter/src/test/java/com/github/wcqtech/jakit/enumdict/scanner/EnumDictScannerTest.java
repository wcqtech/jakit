package com.github.wcqtech.jakit.enumdict.scanner;

import com.github.wcqtech.jakit.enumdict.DictItem;
import com.github.wcqtech.jakit.enumdict.EnumDictRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumDictScannerTest {

    private EnumDictRegistry registry;
    private EnumDictScanner scanner;

    @BeforeEach
    void setUp() {
        registry = new EnumDictRegistry();
        scanner = new EnumDictScanner(registry, new DefaultResourceLoader());
    }

    @Test
    void scansInterfaceAndAnnotationEnums() {
        scanner.scan("com.github.wcqtech.jakit.enumdict.scanner.fixture.basic");

        assertEquals(Set.of("TestOrderStatus", "pay_channel", "TestLevel", "InterfaceType", "TestDefaultType",
                "TestNumericValue"),
                registry.types());

        List<DictItem> statusItems = registry.items("TestOrderStatus");
        assertEquals(2, statusItems.size());
        assertEquals("0", statusItems.get(0).key());
        assertEquals("Pending", statusItems.get(0).value());
        assertEquals("1", statusItems.get(1).key());
        assertEquals("Paid", statusItems.get(1).value());

        Optional<DictItem> wechat = registry.get("pay_channel", "1");
        assertTrue(wechat.isPresent());
        assertEquals("WeChat", wechat.get().value());

        Optional<DictItem> numeric = registry.get("TestNumericValue", "1");
        assertTrue(numeric.isPresent());
        assertEquals("10", numeric.get().value());

        assertTrue(registry.contains("TestLevel", "1"));
        assertTrue(registry.contains("InterfaceType", "1"));
        assertTrue(registry.contains("TestDefaultType", "2"));
    }

    @Test
    void scansMultipleBasePackages() {
        scanner.scan(
                "com.github.wcqtech.jakit.enumdict.scanner.fixture.wildcard.alpha",
                "com.github.wcqtech.jakit.enumdict.scanner.fixture.wildcard.beta");

        assertTrue(registry.contains("AlphaType", "1"));
        assertTrue(registry.contains("BetaType", "1"));
    }

    @Test
    void scansWildcardBasePackage() {
        scanner.scan("com.github.wcqtech.jakit.enumdict.scanner.fixture.wildcard.*");

        assertTrue(registry.contains("AlphaType", "1"));
        assertTrue(registry.contains("BetaType", "1"));
    }

    @Test
    void failsWhenAnnotationFieldIsMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> scanner.scan("com.github.wcqtech.jakit.enumdict.scanner.fixture.invalid.missing"));

        assertTrue(ex.getMessage().contains("DictKey") || ex.getMessage().contains("DictValue"));
    }

    @Test
    void failsWhenDictIsOnNonEnumType() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> scanner.scan("com.github.wcqtech.jakit.enumdict.scanner.fixture.invalid.notenum"));

        assertTrue(ex.getMessage().contains("not an enum"));
    }

    @Test
    void failsWhenTypeIsRegisteredWithDifferentItems() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> scanner.scan("com.github.wcqtech.jakit.enumdict.scanner.fixture.invalid.conflict"));

        Throwable cause = ex.getCause();
        assertTrue(cause != null && cause.getMessage().contains("Duplicate dictionary type"));
    }
}
