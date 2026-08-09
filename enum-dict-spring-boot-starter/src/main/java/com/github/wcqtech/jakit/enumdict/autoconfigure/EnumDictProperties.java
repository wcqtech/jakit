package com.github.wcqtech.jakit.enumdict.autoconfigure;

import com.github.wcqtech.jakit.enumdict.convert.MissingPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for enum dictionary scanning.
 *
 * Prefix: {@code jakit.enum-dict}.
 */
@ConfigurationProperties(prefix = "jakit.enum-dict")
public class EnumDictProperties {

    private boolean enabled = true;

    private List<String> basePackages = new ArrayList<>();

    private final Convert convert = new Convert();

    private final I18n i18n = new I18n();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }

    public Convert getConvert() {
        return convert;
    }

    public I18n getI18n() {
        return i18n;
    }

    /**
     * Configuration for dictionary value conversion.
     */
    public static class Convert {

        private MissingPolicy missingPolicy = MissingPolicy.IGNORE;

        public MissingPolicy getMissingPolicy() {
            return missingPolicy;
        }

        public void setMissingPolicy(MissingPolicy missingPolicy) {
            this.missingPolicy = missingPolicy;
        }
    }

    /**
     * Configuration for dictionary i18n resolution.
     */
    public static class I18n {

        private MissingPolicy missingPolicy = MissingPolicy.IGNORE;

        public MissingPolicy getMissingPolicy() {
            return missingPolicy;
        }

        public void setMissingPolicy(MissingPolicy missingPolicy) {
            this.missingPolicy = missingPolicy;
        }
    }
}
