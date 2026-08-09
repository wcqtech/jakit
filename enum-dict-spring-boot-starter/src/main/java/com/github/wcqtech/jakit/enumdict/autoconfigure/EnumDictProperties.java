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
}
