package com.github.wcqtech.enumdict.autoconfigure;

import com.github.wcqtech.enumdict.scanner.EnumDictScanner;
import com.github.wcqtech.enumdict.service.EnumDictService;
import com.github.wcqtech.enumdict.service.EnumDictUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Objects;

/**
 * Scans and registers enum dictionaries after all singletons are instantiated.
 */
class EnumDictRegistrar implements SmartInitializingSingleton, Ordered {

    private final EnumDictScanner scanner;
    private final EnumDictService service;
    private final EnumDictProperties properties;
    private final BeanFactory beanFactory;

    EnumDictRegistrar(EnumDictScanner scanner, EnumDictService service,
                      EnumDictProperties properties, BeanFactory beanFactory) {
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory must not be null");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> basePackages = resolveBasePackages();
        scanner.scan(basePackages.toArray(new String[0]));
        EnumDictUtils.setService(service);
    }

    private List<String> resolveBasePackages() {
        List<String> configured = properties.getBasePackages();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        if (AutoConfigurationPackages.has(beanFactory)) {
            return AutoConfigurationPackages.get(beanFactory);
        }
        return List.of();
    }
}
