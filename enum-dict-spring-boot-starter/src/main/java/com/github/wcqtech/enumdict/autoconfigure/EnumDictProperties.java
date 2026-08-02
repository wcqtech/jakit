package com.github.wcqtech.enumdict.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for enum dictionary scanning.
 *
 * Prefix: {@code devkit.enum-dict}.
 */
@ConfigurationProperties(prefix = "devkit.enum-dict")
public class EnumDictProperties {

    private boolean enabled = true;

    private List<String> basePackages = new ArrayList<>();

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
}
