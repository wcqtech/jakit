package com.github.wcqtech.enumdict.autoconfigure;

import com.github.wcqtech.enumdict.EnumDictRegistry;
import com.github.wcqtech.enumdict.scanner.EnumDictScanner;
import com.github.wcqtech.enumdict.service.DefaultEnumDictService;
import com.github.wcqtech.enumdict.service.EnumDictService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Auto-configures enum dictionary scanning, registration and query facade.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "devkit.enum-dict", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EnumDictProperties.class)
public class EnumDictAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EnumDictRegistry enumDictRegistry() {
        return new EnumDictRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public EnumDictScanner enumDictScanner(EnumDictRegistry registry, ResourceLoader resourceLoader) {
        return new EnumDictScanner(registry, resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public EnumDictService enumDictService(EnumDictRegistry registry) {
        return new DefaultEnumDictService(registry);
    }

    @Bean
    public EnumDictRegistrar enumDictRegistrar(EnumDictScanner scanner, EnumDictService service,
                                               EnumDictProperties properties, BeanFactory beanFactory) {
        return new EnumDictRegistrar(scanner, service, properties, beanFactory);
    }
}
